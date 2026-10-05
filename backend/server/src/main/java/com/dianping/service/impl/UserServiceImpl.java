package com.dianping.service.impl;

import java.security.SecureRandom;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dianping.VO.TokenVO;
import com.dianping.VO.UserVO;
import com.dianping.constant.RedisConstants;
import com.dianping.constant.SystemConstants;
import com.dianping.dto.RegisterDTO;
import com.dianping.security.TokenService;
import com.dianping.utils.RegexUtils;
import com.dianping.dto.LoginDTO;
import com.dianping.result.Result;
import com.dianping.entity.User;
import com.dianping.mapper.UserMapper;
import com.dianping.service.UserService;
import com.dianping.service.UserInfoService;
import com.dianping.entity.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] RANDOM_ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private static String randomDigits(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(SECURE_RANDOM.nextInt(10));
        }
        return result.toString();
    }

    private static String randomAlphaNumeric(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(RANDOM_ALPHANUMERIC[SECURE_RANDOM.nextInt(RANDOM_ALPHANUMERIC.length)]);
        }
        return result.toString();
    }

    private static final DefaultRedisScript<Long> CONSUME_CODE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate stringRedisTemplate;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserInfoService userInfoService;
    //构造器注入
    public UserServiceImpl(StringRedisTemplate stringRedisTemplate,
                           TokenService tokenService, PasswordEncoder passwordEncoder,
                           UserInfoService userInfoService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenService = tokenService ;
        this.passwordEncoder = passwordEncoder;
        this.userInfoService = userInfoService;
    }

    @Override
    public Result<Void> sendCodeWhenLogin(String phone) {
        //先校验手机号
        //如果格式不对就直接return
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.error("手机号格式错误！") ;
        }

        //顺便校验用户是否存在
        User user = getOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
        );

        if (user == null) {
            return Result.error("用户不存在，请先注册");
        }
        if (!acquireCodeSendPermit("login:", phone)) {
            return Result.error("验证码请求过于频繁，请稍后再试");
        }
        // 格式正确就生成验证码并保存到 Redis
        String code = randomDigits(6);
        log.debug("验证码已生成：{}" , code) ;
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.CODE_TTL,
                TimeUnit.MINUTES);
        log.debug("登录验证码已生成并写入 Redis");
        return Result.success() ;
    }

    @Override
    public Result<Void> sendCodeWhenRegister(String phone){
        //先校验手机号
        //如果格式不对就直接return
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.error("手机号格式错误！") ;
        }

        boolean exists = lambdaQuery()
                .eq(User::getPhone, phone)
                .exists();
        if (exists) {
            return Result.error("手机号已注册");
        }
        if (!acquireCodeSendPermit("register:", phone)) {
            return Result.error("验证码请求过于频繁，请稍后再试");
        }

        // 格式正确就生成验证码并保存到 Redis
        String code = randomDigits(6);
        log.debug("验证码已生成：{}" , code) ;
        stringRedisTemplate.opsForValue().set(
                RedisConstants.REGISTER_CODE_KEY + phone,
                code,
                RedisConstants.CODE_TTL,
                TimeUnit.MINUTES);
        log.debug("注册验证码已生成并写入 Redis");
        return Result.success() ;
    }

    @Override
    public Result<UserVO> queryUserById(Long userId) {
        if (userId == null || userId <= 0) {
            return Result.error("用户ID非法");
        }
        User user = getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        UserInfo info = userInfoService.getOrCreate(userId);
        if (info == null) {
            return Result.error("用户详情获取失败");
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setCity(info.getCity());
        vo.setIntroduce(info.getIntroduce());
        vo.setFans(info.getFans());
        vo.setFollowee(info.getFollowee());
        return Result.success(vo);
    }

    @Override
    public Result<TokenVO> login(LoginDTO loginDTO) {
        String loginType = loginDTO.getLoginType();
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(loginDTO.getPhone())) {
            return Result.error("手机号格式错误");
        }
        //校验登录类型，避免无效请求仍然查询数据库
        boolean codeLogin = Objects.equals(
                loginType,
                SystemConstants.CODE_LOGIN
        );

        boolean passwordLogin = Objects.equals(
                loginType,
                SystemConstants.PASSWORD_LOGIN
        );

        if (!codeLogin && !passwordLogin) {
            return Result.error("错误的登录类型");
        }

        //利用Mybatis-plus的IService接口提供的getOne方法快速根据phone查询用户
        User user = getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getPhone, loginDTO.getPhone()));
        if(user == null )return Result.error("用户不存在，请先注册") ;

        if(codeLogin) return loginByCode(loginDTO , user) ;

        return loginByPassword(loginDTO ,user) ;

    }

    @Override
    @Transactional
    public Result<Void> register(RegisterDTO registerDTO) {
        String phone = registerDTO.getPhone();

        // 1.确认两次密码一致
        if (!Objects.equals(
                registerDTO.getPassword(),
                registerDTO.getConfirmPassword())) {
            return Result.error("两次输入的密码不一致");
        }

        // 2.判断手机号是否已经注册
        boolean exists = lambdaQuery()
                .eq(User::getPhone, phone)
                .exists();

        if (exists) {
            return Result.error("手机号已注册");
        }

        // 3.读取注册验证码
        String codeKey =
                RedisConstants.REGISTER_CODE_KEY + phone;

        String storedCode = stringRedisTemplate
                .opsForValue()
                .get(codeKey);

        if (storedCode == null) {
            return Result.error("验证码已失效");
        }

        if (!Objects.equals(
                storedCode,
                registerDTO.getVerifyCode())) {
            return Result.error("验证码错误");
        }

        if (!consumeCode(codeKey, registerDTO.getVerifyCode())) {
            return Result.error("验证码已失效");
        }

        // 4.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setPassword(
                passwordEncoder.encode(
                        registerDTO.getPassword()
                )
        );
        user.setNickName(
                SystemConstants.USER_NICK_NAME_PREFIX
                        + randomAlphaNumeric(10)
        );

        // 5.保存数据库
        save(user);
        userInfoService.getOrCreate(user.getId());

        return Result.success();
    }

    private Result<TokenVO> loginByCode(LoginDTO loginDTO, User user) {
        //从redis里查询验证码
        String storedCode = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_CODE_KEY + loginDTO.getPhone()) ;
        if(storedCode != null){

            if(Objects.equals(storedCode, loginDTO.getVerifyCode())){
                String codeKey = RedisConstants.LOGIN_CODE_KEY + loginDTO.getPhone();
                if (!consumeCode(codeKey, loginDTO.getVerifyCode())) {
                    return Result.error("验证码已失效");
                }
                return Result.success(tokenService.issue(String.valueOf(user.getId()))) ;
            }

            else {
                return Result.error("验证码错误") ;
            }
        }

        else{
            return Result.error("验证码已失效") ;
        }
        }

    private boolean consumeCode(String codeKey, String expectedCode) {
        Long consumed = stringRedisTemplate.execute(
                CONSUME_CODE_SCRIPT,
                Collections.singletonList(codeKey),
                expectedCode
        );
        return Long.valueOf(1L).equals(consumed);
    }

    private boolean acquireCodeSendPermit(String purpose, String phone) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisConstants.CODE_SEND_COOLDOWN_KEY + purpose + phone,
                "1",
                RedisConstants.CODE_SEND_COOLDOWN_TTL,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(acquired);
    }

        private Result<TokenVO> loginByPassword(LoginDTO loginDTO, User user) {
            String rawPassword = loginDTO.getPassword();
            if(rawPassword == null || rawPassword.isEmpty()){
                return Result.error("密码不能为空") ;
            }
            boolean matched = passwordEncoder.matches(rawPassword,
                    user.getPassword());
            if (!matched) {
                return Result.error("用户名或密码错误");
            }
            return Result.success(tokenService.issue(String.valueOf(user.getId())));

        }
}
