package com.dianping.service.impl;

import com.dianping.dto.UserDTO;
import com.dianping.entity.Follow;
import com.dianping.entity.User;
import com.dianping.entity.UserInfo;
import com.dianping.result.Result;
import com.dianping.mapper.FollowMapper;
import com.dianping.service.FollowService;
import com.dianping.service.UserInfoService;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserInfoService userInfoService;

    @Override
    @Transactional
    public Result<Void> follow(Long followUserId, Boolean toFollow) {
        if (followUserId == null || followUserId <= 0) {
            return Result.error("用户ID非法");
        }
        if (toFollow == null) {
            return Result.error("关注状态不能为空");
        }
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return Result.error("请先登录");
        }
        Long userId = currentUser.getId();
        if (userId.equals(followUserId)) {
            return Result.error("不能关注自己");
        }
        User target = userService.getById(followUserId);
        if (target == null) {
            return Result.error("用户不存在");
        }
        //查询数据库中是否已有关注关系
        boolean exists = lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .exists();

        //toFollow为true就表明本次要执行关注操作，为false就表明本次要执行取关
        if (toFollow) {
            if (exists) {
                return Result.success();
            }
            try {//保存关注关系到follow表
                if (!save(new Follow().setUserId(userId).setFollowUserId(followUserId))) {
                    return Result.error("关注失败");
                }
            } catch (DataIntegrityViolationException ex) {
                // A concurrent request created the unique relation first.
                return Result.success();
            }//再接着修改用户详情
            UserInfo currentInfo = userInfoService.getOrCreate(userId);
            UserInfo targetInfo = userInfoService.getOrCreate(followUserId);
            if (currentInfo == null
                    || targetInfo == null
                    || !increment(userId, "followee")
                    || !increment(followUserId, "fans")) {
                throw new IllegalStateException("关注计数更新失败");
            }
            return Result.success();
        }

        if (!exists) {
            return Result.success();
        }
        boolean removed = remove(lambdaQuery()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)
                .getWrapper());
        if (!removed) {
            return Result.error("取消关注失败");
        }
        UserInfo currentInfo = userInfoService.getOrCreate(userId);
        UserInfo targetInfo = userInfoService.getOrCreate(followUserId);
        if (currentInfo == null
                || targetInfo == null
                || !decrement(userId, "followee")
                || !decrement(followUserId, "fans")) {
            throw new IllegalStateException("关注计数更新失败");
        }
        return Result.success();
    }

    //true：已经关注，false：尚未关注
    @Override
    public Result<Boolean> isFollow(Long followUserId) {
        if (followUserId == null || followUserId <= 0) {
            return Result.error("用户ID非法");
        }
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return Result.error("请先登录");
        }
        if (userService.getById(followUserId) == null) {
            return Result.error("用户不存在");
        }
        boolean exists = lambdaQuery()
                .eq(Follow::getUserId, currentUser.getId())
                .eq(Follow::getFollowUserId, followUserId)
                .exists();
        return Result.success(exists);
    }

    private boolean increment(Long userId, String field) {
        return userInfoService.update()
                .setSql(field + " = COALESCE(" + field + ", 0) + 1")
                .eq("user_id", userId)
                .update();
    }

    private boolean decrement(Long userId, String field) {
        return userInfoService.update()
                .setSql(field + " = GREATEST(COALESCE(" + field + ", 0) - 1, 0)")
                .eq("user_id", userId)
                .update();
    }

}
