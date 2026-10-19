package com.dianping.shopdetail.enums;

import java.util.Arrays;

/**
 * 稳定代码用于持久化，展示名称可独立调整。
 */
public enum ReviewTag {
    /**
     * 味道好。
     */
    TASTY("味道好"),
    /**
     * 环境好。
     */
    NICE_ENVIRONMENT("环境好"),
    /**
     * 服务热情。
     */
    FRIENDLY_SERVICE("服务热情"),
    /**
     * 价格实惠。
     */
    GOOD_VALUE("价格实惠"),
    /**
     * 干净卫生。
     */
    CLEAN("干净卫生"),
    /**
     * 交通方便。
     */
    CONVENIENT_LOCATION("交通方便"),
    /**
     * 出餐快。
     */
    FAST_SERVICE("出餐快"),
    /**
     * 停车方便，仅代表用户口碑。
     */
    EASY_PARKING("停车方便");
    /**
     * 系统预设标签的中文展示名称。
     */
    private final String displayName;
    /**
     * 创建 ReviewTag，初始化所需依赖及实例状态。
     *
     * @param displayName 中文展示名称
     */
    ReviewTag(String displayName) { this.displayName = displayName; }
    /**
     * 取得预设标签的中文展示名称。
     *
     * @return 标签对应的中文展示名称
     */
    public String displayName() { return displayName; }
    /**
     * 检查标签代码是否属于系统预设词典。
     *
     * @param code 标签代码
     * @return 代码是否属于预设标签
     */
    public static boolean contains(String code) {
        return Arrays.stream(values()).anyMatch(tag -> tag.name().equals(code));
    }
}
