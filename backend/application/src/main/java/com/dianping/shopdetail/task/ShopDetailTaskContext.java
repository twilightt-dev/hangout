package com.dianping.shopdetail.task;
import com.dianping.shared.taskchain.model.TaskContext;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.model.result.SectionResult;
import java.time.LocalDateTime;
import java.util.*;

/** 业务数据全部经父类属性存储，以参与快照、增量合并和封存。 */
public class ShopDetailTaskContext extends TaskContext<Long, ShopDetailAggregate> {
    public static final String GROUP = "shop-detail";
    public ShopDetailTaskContext(Long shopId) { super(shopId); setAttribute("since", LocalDateTime.now().minusDays(90)); }
    public ShopDetailTaskContext(Long shopId, Long viewerId) { this(shopId); setAttribute("viewerId", viewerId); }
    private ShopDetailTaskContext(ShopDetailTaskContext parent, Snapshot<ShopDetailAggregate> snapshot) { super(parent, snapshot); }
    @Override protected ShopDetailTaskContext createNodeContext(Snapshot<ShopDetailAggregate> snapshot) {
        return new ShopDetailTaskContext(this, snapshot);
    }
    public Long getShopId() { return getInput(); }
    public Long getViewerId() { return (Long) getAttribute("viewerId"); }
    public LocalDateTime getSince() { return (LocalDateTime) getAttribute("since"); }
    public ShopBaseInfo getBase() { return (ShopBaseInfo) getAttribute("base"); }
    public void setBase(ShopBaseInfo value) { setAttribute("base", value); }
    public void put(String key, SectionResult<?> result) { setAttribute(key, result); }
    @SuppressWarnings("unchecked")
    public <T> SectionResult<T> section(String key, String nodeId) {
        var value = (SectionResult<T>) getAttribute(key);
        if (value != null) return value;
        boolean timeout = getNodeResults().stream().anyMatch(node -> node.nodeId().equals(nodeId)
                && node.status() == com.dianping.shared.taskchain.enums.NodeExecutionStatus.TIMED_OUT);
        return SectionResult.unavailable(timeout ? "TIMED_OUT" : "FAILED", timeout ? "加载超时，请重试" : "暂时不可用，请重试");
    }
    @SuppressWarnings("unchecked")
    public Set<Long> getRecommendedIds() {
        var value = (Set<Long>) getAttribute("recommendedIds"); return value == null ? Set.of() : value;
    }
}
