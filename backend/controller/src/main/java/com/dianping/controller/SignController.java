package com.dianping.controller;

import com.dianping.VO.SignResultVO;
import com.dianping.VO.SignStatsVO;
import com.dianping.result.Result;
import com.dianping.service.SignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/sign")
@Tag(name = "用户签到")
public class SignController {
    private final SignService service;

    public SignController(SignService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "当前用户当天签到")
    public Result<SignResultVO> sign() {
        return Result.success(service.sign());
    }

    @GetMapping("/stats")
    @Operation(summary = "查询本月签到统计及上月比较")
    public Result<SignStatsVO> stats() {
        return Result.success(service.stats());
    }
}
