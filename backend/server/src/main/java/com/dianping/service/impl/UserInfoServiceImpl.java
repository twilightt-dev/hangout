package com.dianping.service.impl;

import com.dianping.dto.UpdateUserInfoDTO;
import com.dianping.entity.UserInfo;
import com.dianping.mapper.UserInfoMapper;
import com.dianping.result.Result;
import com.dianping.service.UserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.dto.UserDTO;
import com.dianping.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    @Override
    public UserInfo getOrCreate(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        UserInfo info = getById(userId);
        if (info != null) {
            return info;
        }
        info = new UserInfo()
                .setUserId(userId)
                .setCity("")
                .setFans(0)
                .setFollowee(0)
                .setGender(false)
                .setCredits(0)
                .setLevel(false);
        try {
            if (save(info)) {
                return info;
            }
            return getById(userId);
        } catch (DataIntegrityViolationException ex) {
            // Another request may have initialized the same user's row first.
            return getById(userId);
        }
    }

    @Override
    public Result<Void> updateCurrent(UpdateUserInfoDTO request) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return Result.error("请先登录");
        }
        if (request == null) {
            return Result.error("资料不能为空");
        }

        UserInfo info = getOrCreate(currentUser.getId());
        if (info == null) {
            return Result.error("用户不存在");
        }
        if (request.getCity() != null) {
            info.setCity(request.getCity());
        }
        if (request.getIntroduce() != null) {
            info.setIntroduce(request.getIntroduce());
        }
        if (request.getGender() != null) {
            info.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            info.setBirthday(request.getBirthday());
        }
        if (!updateById(info)) {
            return Result.error("资料更新失败");
        }
        return Result.success();
    }

}
