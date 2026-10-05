package com.dianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.VO.TokenVO;
import com.dianping.VO.UserVO;
import com.dianping.dto.LoginDTO;
import com.dianping.dto.RegisterDTO;
import com.dianping.result.Result;
import com.dianping.entity.User;
import jakarta.validation.Valid;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface UserService extends IService<User> {

    Result<Void> sendCodeWhenLogin(String phone);

    Result<TokenVO> login(LoginDTO loginDTO);

    Result<Void> register(@Valid RegisterDTO registerDTO);

    Result<Void> sendCodeWhenRegister(String phone);

    Result<UserVO> queryUserById(Long userId);
}
