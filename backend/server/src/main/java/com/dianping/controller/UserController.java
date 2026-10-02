package com.dianping.controller;


import com.dianping.VO.TokenVO;
import com.dianping.dto.LoginDTO;
import com.dianping.dto.RefreshTokenDTO;
import com.dianping.dto.RegisterDTO;
import com.dianping.dto.UserDTO;
import com.dianping.dto.UpdateUserInfoDTO;
import com.dianping.entity.UserInfo;
import com.dianping.result.Result;
import com.dianping.security.TokenService;
import com.dianping.service.UserInfoService;
import com.dianping.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import com.dianping.utils.UserHolder;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户接口")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserInfoService userInfoService;

    @Autowired
    private TokenService tokenService;
    /**
     * 登录时发送手机验证码
     */
    @PostMapping("/login/code")
    @Operation(summary = "登录时发送验证码")
    public Result<Void> sendCodeWhenLogin(@RequestParam("phone") String phone) {
        return userService.sendCodeWhenLogin(phone) ;
    }


    /**
     * 注册时发送验证码
     * @param phone
     * @return
     */
    @PostMapping("/register/code")
    @Operation(summary = "注册时发送验证码")
    public Result<Void> sendCodeWhenRegister(@RequestParam("phone") String phone){
        return userService.sendCodeWhenRegister(phone) ;
    }

    /**
     * 登录接口
     * @param loginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "登录")
    public Result<TokenVO> login( @Valid @RequestBody LoginDTO loginDTO) {
        return userService.login(loginDTO) ;
    }


    /**
     * 注册接口
     * @param registerDTO
     * @return
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(
            @Valid @RequestBody RegisterDTO registerDTO) {

        return userService.register(registerDTO);
    }




    /**
     * 刷新接口，用于刷新refreshToken
     * @param refreshTokenDTO
     * @return TokenVO
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token")
    public Result<TokenVO> refresh(
            @Valid @RequestBody RefreshTokenDTO refreshTokenDTO
    ){
        TokenVO tokenVO = tokenService.refresh(refreshTokenDTO.getRefreshToken()) ;
        return Result.success(tokenVO) ;

    }


    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出登录")
    public Result<Void> logout(
            @Valid @RequestBody RefreshTokenDTO refreshTokenDTO) {
        tokenService.revoke(refreshTokenDTO.getRefreshToken());
        return Result.success();
    }

    @GetMapping("/me")
    @Operation(summary = "查询当前登录用户")
    public Result<UserDTO> me(){

        return Result.success(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    @Operation(summary = "查询用户详情")
    public Result<UserInfo> info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            if (userService.getById(userId) == null) {
                return Result.error("用户不存在");
            }
            info = userInfoService.getOrCreate(userId);
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.success(info);
    }

    @PutMapping("/info")
    @Operation(summary = "更新当前用户详情")
    public Result<Void> updateInfo(@Valid @RequestBody UpdateUserInfoDTO request) {
        return userInfoService.updateCurrent(request);
    }
}
