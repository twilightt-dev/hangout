package com.dianping.service;

import com.dianping.VO.SignResultVO;
import com.dianping.VO.SignStatsVO;

public interface SignService {
    SignResultVO sign();

    SignStatsVO stats();
}
