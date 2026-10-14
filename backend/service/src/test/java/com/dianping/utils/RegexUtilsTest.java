package com.dianping.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexUtilsTest {

    @Test
    void acceptsCurrentMainlandMobilePrefixesUsedByRegistrationContract() {
        assertThat(RegexUtils.isPhoneInvalid("19112345678")).isFalse();
        assertThat(RegexUtils.isPhoneInvalid("19312345678")).isFalse();
        assertThat(RegexUtils.isPhoneInvalid("12912345678")).isTrue();
    }
}
