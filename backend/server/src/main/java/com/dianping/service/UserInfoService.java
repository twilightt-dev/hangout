package com.dianping.service;

import com.dianping.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.dto.UpdateUserInfoDTO;
import com.dianping.result.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface UserInfoService extends IService<UserInfo> {

    UserInfo getOrCreate(Long userId);

    Result<Void> updateCurrent(UpdateUserInfoDTO request);

}
