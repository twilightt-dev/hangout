package com.dianping.controller;


import com.dianping.VO.CommonFollowVO;
import com.dianping.result.Result;
import com.dianping.service.FollowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/follow")
@Tag(name = "关注接口")
public class FollowController {

    @Autowired
    private FollowService followService;

    @GetMapping("/or/not/{id}")
    @Operation(summary = "查询关注状态")
    public Result<Boolean> isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @PutMapping("/{id}/{toFollow}")
    @Operation(summary = "关注或取消关注用户")
    public Result<Void> follow(@PathVariable("id") Long followUserId,
                               @PathVariable("toFollow") Boolean toFollow) {
        return followService.follow(followUserId, toFollow);
    }

    @GetMapping("/common/{id}")
    @Operation(summary = "查询共同关注")
    public Result<List<CommonFollowVO>> common(@PathVariable("id") Long followUserId) {
        return followService.queryCommonFollows(followUserId);
    }



}
