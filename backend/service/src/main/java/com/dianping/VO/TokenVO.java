package com.dianping.VO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenVO {
    String accessToken;
    String refreshToken;
    String tokenType ;
    //accessToken过期时间
    Long accessTtl;
}
