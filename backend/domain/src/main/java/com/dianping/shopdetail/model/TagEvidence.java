package com.dianping.shopdetail.model;
import com.dianping.shopdetail.enums.ReviewTag;
import java.util.List;
/**
 * 一位作者的一条标签证据，预设标签和博客纯文本分开承载。
 *
 * @param userId 服务端确认的用户标识
 * @param tags 用户选择的预设标签
 * @param text 用于匹配口碑标签的文本
 */
public record TagEvidence(Long userId, List<ReviewTag> tags, String text) {
    /**
     * 创建 TagEvidence，初始化所需依赖及实例状态。
     *
     * @param userId 服务端确认的用户标识
     * @param tags 用户选择的预设标签
     * @param text 用于匹配口碑标签的文本
     */
    public TagEvidence { tags = tags == null ? List.of() : List.copyOf(tags); }
}
