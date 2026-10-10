package com.dianping.controller;

import com.dianping.VO.VisitStatsVO;
import com.dianping.dto.VisitRequestDTO;
import com.dianping.result.Result;
import com.dianping.service.VisitStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "详情访问统计")
public class VisitStatsController {
    private final VisitStatsService service;

    public VisitStatsController(VisitStatsService service) { this.service = service; }

    @PostMapping("/blog/{id}/visit")
    @Operation(summary = "记录博客展示并返回累计浏览次数")
    public Result<VisitStatsVO> blog(@PathVariable Long id, @RequestBody VisitRequestDTO event) {
        return Result.success(service.recordBlog(id, event));
    }

    @PostMapping("/shop/{id}/visit")
    @Operation(summary = "记录店铺展示并返回近似累计访客数")
    public Result<VisitStatsVO> shop(@PathVariable Long id, @RequestBody VisitRequestDTO event) {
        return Result.success(service.recordShop(id, event));
    }
}
