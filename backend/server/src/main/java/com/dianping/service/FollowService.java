package com.dianping.service;

import com.dianping.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.result.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowService extends IService<Follow> {

    Result<Void> follow(Long followUserId, Boolean isFollow);

    Result<Boolean> isFollow(Long followUserId);

}
