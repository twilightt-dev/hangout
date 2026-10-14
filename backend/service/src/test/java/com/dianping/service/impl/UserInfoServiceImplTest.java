package com.dianping.service.impl;

import com.dianping.dto.UpdateUserInfoDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserInfoServiceImplTest {

    @Test
    void updateCurrentRequiresLogin() {
        assertEquals(0, new UserInfoServiceImpl()
                .updateCurrent(new UpdateUserInfoDTO())
                .getCode());
    }
}
