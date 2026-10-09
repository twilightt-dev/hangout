package com.dianping.service.impl;

import com.dianping.VO.SignResultVO;
import com.dianping.VO.SignStatsVO;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.UserDTO;
import com.dianping.service.SignService;
import com.dianping.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SignServiceImpl implements SignService {
    //业务时区
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    //将日期格式化为六位字符串供redisKey使用
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private final StringRedisTemplate redis;
    //java提供的时钟抽象，包含时间来源和时区信息
    private final Clock clock;

    @Autowired
    public SignServiceImpl(StringRedisTemplate redis) {
        this(redis, Clock.system(BUSINESS_ZONE));
    }

    public SignServiceImpl(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Override
    public SignResultVO sign() {
        Long userId = currentUserId();
        LocalDate today = LocalDate.now(clock);
        //旧值为null说明有异常
        Boolean previous = redis.opsForValue().setBit(key(userId, YearMonth.from(today)),
                today.getDayOfMonth() - 1L, true);
        if (previous == null) {
            throw new DataAccessResourceFailureException("Redis did not acknowledge sign-in");
        }
        return new SignResultVO(today.toString(), previous);
    }

    //统计签到数据
    @Override
    public SignStatsVO stats() {
        Long userId = currentUserId();
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        YearMonth previousMonth = month.minusMonths(1);

        long currentBits = readBits(key(userId, month), today.getDayOfMonth());
        long previousBits = readBits(key(userId, previousMonth), previousMonth.lengthOfMonth());
        //Long.bitCount方法用于统计一个long的二进制表示中有多少个1
        int monthlyDays = Long.bitCount(currentBits);
        int previousMonthDays = Long.bitCount(previousBits);
        int continuousDays = 0;
        //从最高位第一天开始往右边一个个统计，和1进行位与&即可
        for (long bits = currentBits; (bits & 1L) != 0; bits >>>= 1) {
            continuousDays++;
        }
        return new SignStatsVO(today.toString(), month.format(MONTH_FORMAT),
                (currentBits & 1L) != 0, monthlyDays, continuousDays,
                previousMonthDays, monthlyDays - previousMonthDays);
    }

    /**
     * 从指定的bitmap的第0位开始读取days个bit
     * @param key redisKey
     * @param days 第几天
     * @return 二进制表示的整数
     */
    private long readBits(String key, int days) {
        List<Long> result = redis.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(days)).valueAt(0));
        if (result == null || result.size() != 1 || result.getFirst() == null) {
            throw new DataAccessResourceFailureException("Redis服务异常");
        }
        return result.getFirst();
    }

    //生成key
    private static String key(Long userId, YearMonth month) {
        return RedisConstants.USER_SIGN_KEY + userId + ":" + month.format(MONTH_FORMAT);
    }
    //获取当前用户ID
    private static Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Sign-in requires a logged-in user");
        }
        return user.getId();
    }
}
