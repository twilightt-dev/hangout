package com.dianping.utils;

import com.dianping.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    //设置成UserDTO是为了隐藏用户敏感信息，只保留最基本的id之类的即可
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
