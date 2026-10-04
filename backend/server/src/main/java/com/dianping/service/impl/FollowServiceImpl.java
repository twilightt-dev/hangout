package com.dianping.service.impl;

import com.dianping.VO.CommonFollowVO;
import com.dianping.constant.RedisConstants;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
                syncFollowSet(userId, followUserId);
                return Result.success();
            }
            try {//保存关注关系到follow表
                if (!save(new Follow().setUserId(userId).setFollowUserId(followUserId))) {
                    return Result.error("关注失败");
                }
            } catch (DataIntegrityViolationException ex) {
                // A concurrent request created the unique relation first.
                syncFollowSet(userId, followUserId);
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
            syncFollowSet(userId, followUserId);
            return Result.success();
        }

        if (!exists) {
            stringRedisTemplate.opsForSet().remove(followSetKey(userId), String.valueOf(followUserId));
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
        stringRedisTemplate.opsForSet().remove(followSetKey(userId), String.valueOf(followUserId));
        return Result.success();
    }

    /**
     * 查询共同关注
     * @param followUserId
     * @return
     */
    @Override
    public Result<List<CommonFollowVO>> queryCommonFollows(Long followUserId) {
        if (followUserId == null || followUserId <= 0) {
            return Result.error("用户ID非法");
        }
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return Result.error("请先登录");
        }
        Long currentUserId = currentUser.getId();
        if (userService.getById(followUserId) == null) {
            return Result.error("用户不存在");
        }
        if (currentUserId.equals(followUserId)) {
            return Result.success(Collections.emptyList());
        }

        ensureFollowSet(currentUserId);
        ensureFollowSet(followUserId);

        Set<String> commonIds = stringRedisTemplate.opsForSet().intersect(
                followSetKey(currentUserId), followSetKey(followUserId));
        if (commonIds == null || commonIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> userIds = commonIds.stream()
                .map(this::parseUserId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<User> users = userService.listByIds(userIds);
        List<CommonFollowVO> result = new ArrayList<>(users.size());
        for (User user : users) {
            CommonFollowVO vo = new CommonFollowVO();
            vo.setId(user.getId());
            vo.setNickName(user.getNickName());
            vo.setIcon(user.getIcon());
            result.add(vo);
        }
        return Result.success(result);
    }

    // Redis 集合存在时直接走缓存；只有缓存缺失时才从数据库重建。
    private void ensureFollowSet(Long userId) {
        String key = followSetKey(userId);
        if (stringRedisTemplate.hasKey(key)) {
            return;
        }
        rebuildFollowSet(key, loadFollowIds(userId));
    }

    private List<String> loadFollowIds(Long userId) {
        return lambdaQuery()
                .eq(Follow::getUserId, userId)
                .select(Follow::getFollowUserId)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private void rebuildFollowSet(String key, List<String> followIds) {
        stringRedisTemplate.delete(key);
        if (!followIds.isEmpty()) {
            stringRedisTemplate.opsForSet().add(key, followIds.toArray(String[]::new));
        }
    }

    // 关注关系变更后重建完整集合，避免冷缓存只写入本次目标而丢失历史关注。
    private void syncFollowSet(Long userId, Long ignoredFollowUserId) {
        rebuildFollowSet(followSetKey(userId), loadFollowIds(userId));
    }
    //统一生成redis的key
    private String followSetKey(Long userId) {
        return RedisConstants.FOLLOW_USER_KEY + userId;
    }
    //把redis里String类型的id转成Long类型
    private Long parseUserId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

