package com.dianping.shopdetail.model;
import com.dianping.shopdetail.enums.ReviewTag;
import java.util.List;
/** 一位作者的一条标签证据，预设标签和博客纯文本分开承载。 */
public record TagEvidence(Long userId, List<ReviewTag> tags, String text) {
    public TagEvidence { tags = tags == null ? List.of() : List.copyOf(tags); }
}
