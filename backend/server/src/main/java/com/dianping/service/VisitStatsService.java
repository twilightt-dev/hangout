package com.dianping.service;

import com.dianping.VO.VisitStatsVO;
import com.dianping.dto.VisitRequestDTO;

public interface VisitStatsService {
    VisitStatsVO recordBlog(Long id, VisitRequestDTO event);
    VisitStatsVO recordShop(Long id, VisitRequestDTO event);
}
