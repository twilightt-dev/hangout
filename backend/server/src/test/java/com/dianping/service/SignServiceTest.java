package com.dianping.service;

import com.dianping.dto.UserDTO;
import com.dianping.service.impl.SignServiceImpl;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SignServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        UserDTO user = new UserDTO();
        user.setId(1001L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void clearUser() { UserHolder.removeUser(); }

    private SignService serviceAt(String instant) {
        return new SignServiceImpl(redis, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void usesShanghaiDateAndReturnsOldBitForRepeatedSignIn() {
        when(values.setBit("sign:1001:202610", 7L, true)).thenReturn(false, true);
        SignService service = serviceAt("2026-10-07T16:00:00Z");
        assertThat(service.sign().date()).isEqualTo("2026-10-08");
        assertThat(service.sign().alreadySigned()).isTrue();
    }

    @Test
    void countsSetDaysAndTrailingStreakFromToday() {
        when(values.bitField(eq("sign:1001:202610"), any())).thenReturn(List.of(167L));
        when(values.bitField(eq("sign:1001:202609"), any())).thenReturn(List.of(7L));
        var stats = serviceAt("2026-10-08T04:00:00Z").stats();
        assertThat(stats.month()).isEqualTo("202610");
        assertThat(stats.todaySigned()).isTrue();
        assertThat(stats.monthlyDays()).isEqualTo(5);
        assertThat(stats.continuousDays()).isEqualTo(3);
        assertThat(stats.previousMonthDays()).isEqualTo(3);
        assertThat(stats.monthDifference()).isEqualTo(2);
    }

    @Test
    void unsignedTodayDoesNotContinueYesterdaysStreak() {
        when(values.bitField(eq("sign:1001:202610"), any())).thenReturn(List.of(166L));
        when(values.bitField(eq("sign:1001:202609"), any())).thenReturn(List.of(31L));
        var stats = serviceAt("2026-10-08T04:00:00Z").stats();
        assertThat(stats.todaySigned()).isFalse();
        assertThat(stats.monthlyDays()).isEqualTo(4);
        assertThat(stats.continuousDays()).isZero();
        assertThat(stats.monthDifference()).isEqualTo(-1);
    }

    @Test
    void comparesJanuaryWithPreviousYearAndDoesNotContinueAcrossMonths() {
        when(values.bitField(eq("sign:1001:202701"), any())).thenReturn(List.of(1L));
        when(values.bitField(eq("sign:1001:202612"), any())).thenReturn(List.of(2147483647L));
        var stats = serviceAt("2026-12-31T16:00:00Z").stats();
        assertThat(stats.month()).isEqualTo("202701");
        assertThat(stats.continuousDays()).isEqualTo(1);
        assertThat(stats.previousMonthDays()).isEqualTo(31);
        assertThat(stats.monthDifference()).isEqualTo(-30);
    }

    @Test
    void countsAll31DaysWithoutSignedIntegerOverflow() {
        when(values.bitField(eq("sign:1001:202610"), any())).thenReturn(List.of(2147483647L));
        when(values.bitField(eq("sign:1001:202609"), any())).thenReturn(List.of(0L));
        var stats = serviceAt("2026-10-31T04:00:00Z").stats();
        assertThat(stats.monthlyDays()).isEqualTo(31);
        assertThat(stats.continuousDays()).isEqualTo(31);
    }

    @Test
    void missingMonthsAreZero() {
        when(values.bitField(anyString(), any())).thenReturn(List.of(0L));
        var stats = serviceAt("2026-10-08T04:00:00Z").stats();
        assertThat(stats.todaySigned()).isFalse();
        assertThat(stats.monthlyDays()).isZero();
        assertThat(stats.continuousDays()).isZero();
        assertThat(stats.previousMonthDays()).isZero();
        assertThat(stats.monthDifference()).isZero();
    }

    @Test
    void redisFailureIsNotTreatedAsZeroSignIns() {
        when(values.bitField(anyString(), any())).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> serviceAt("2026-10-08T04:00:00Z").stats())
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void missingRedisAcknowledgementIsNotTreatedAsSuccessfulSignInOrZeroStats() {
        when(values.setBit(anyString(), anyLong(), eq(true))).thenReturn(null);
        when(values.bitField(anyString(), any())).thenReturn(null);
        SignService service = serviceAt("2026-10-08T04:00:00Z");
        assertThatThrownBy(service::sign).isInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(service::stats).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void anonymousUserCannotWriteSignIn() {
        UserHolder.removeUser();
        assertThatThrownBy(() -> serviceAt("2026-10-08T04:00:00Z").sign())
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(values);
    }
}
