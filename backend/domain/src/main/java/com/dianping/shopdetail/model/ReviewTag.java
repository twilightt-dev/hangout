package com.dianping.shopdetail.model;

import java.util.Arrays;

/** 稳定代码用于持久化，展示名称可独立调整。 */
public enum ReviewTag {
    TASTY("味道好"), NICE_ENVIRONMENT("环境好"), FRIENDLY_SERVICE("服务热情"),
    GOOD_VALUE("价格实惠"), CLEAN("干净卫生"), CONVENIENT_LOCATION("交通方便"),
    FAST_SERVICE("出餐快"), EASY_PARKING("停车方便");
    private final String displayName;
    ReviewTag(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
    public static boolean contains(String code) {
        return Arrays.stream(values()).anyMatch(tag -> tag.name().equals(code));
    }
}
