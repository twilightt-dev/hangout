package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.model.TaskContext;
import com.dianping.shop.shopdetail.model.ShopBaseInfo;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 业务数据经父类属性传递；执行状态留在应用上下文中。
 */
public class ShopDetailTaskContext extends TaskContext<Long, ShopDetailAggregate> {
    /**
     * 商详任务组标识。
     */
    public static final String GROUP = "shop-detail";
    /**
     * 应用分项名称与提供该结果的任务节点之间的对应关系。
     */
    private static final Map<String, String> SECTION_NODES = Map.of(
            "vouchers", "vouchers", "setmeals", "setmeals",
            "recommendations", "recommendations", "recommendationState", "recommendations",
            "reviews", "reviews", "statistics", "reviews", "blogs", "blogs",
            "decisionTags", "enrich", "reviewTags", "enrich", "relatedShops", "enrich");

    /**
     * 创建 ShopDetailTaskContext，初始化所需依赖及实例状态。
     *
     * @param shopId 店铺标识
     */
    public ShopDetailTaskContext(Long shopId) { super(shopId); setAttribute("since", LocalDateTime.now().minusDays(90)); }
    /**
     * 创建 ShopDetailTaskContext，初始化所需依赖及实例状态。
     *
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     */
    public ShopDetailTaskContext(Long shopId, Long viewerId) { this(shopId, viewerId, true); }
    /**
     * 创建可控制是否查询实时优惠券的商详上下文。
     *
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @param includeVouchers 是否在本次聚合中读取优惠券
     */
    public ShopDetailTaskContext(Long shopId, Long viewerId, boolean includeVouchers) {
        this(shopId);
        setAttribute("viewerId", viewerId);
        setAttribute("includeVouchers", includeVouchers);
    }
    /**
     * 创建 ShopDetailTaskContext，初始化所需依赖及实例状态。
     *
     * @param parent 创建当前节点视图的父上下文
     * @param snapshot 节点可见或准备提交的数据快照
     */
    private ShopDetailTaskContext(ShopDetailTaskContext parent, Snapshot<ShopDetailAggregate> snapshot) { super(parent, snapshot); }
    /**
     * 基于引擎提供的快照创建同类型节点视图，业务属性继续通过父类写集传递。
     *
     * @param snapshot 节点可见或准备提交的数据快照
     * @return 与根上下文具有相同业务类型的全新节点视图
     */
    @Override protected ShopDetailTaskContext createNodeContext(Snapshot<ShopDetailAggregate> snapshot) {
        return new ShopDetailTaskContext(this, snapshot);
    }
    /**
     * 取得本次商详查询的店铺标识。
     *
     * @return 本次查询的店铺标识
     */
    public Long getShopId() { return getInput(); }
    /**
     * 取得显式传入的浏览者身份，不读取工作线程的登录状态。
     *
     * @return 当前视图可见的业务值；尚未提供时为 {@code null}
     */
    public Long getViewerId() { return (Long) getAttribute("viewerId"); }
    /**
     * 判断当前聚合是否需要读取优惠券区域。
     *
     * @return 是否读取优惠券
     */
    public boolean includesVouchers() { return !Boolean.FALSE.equals(getAttribute("includeVouchers")); }
    /**
     * 取得本次请求统一使用的近期口碑统计起点。
     *
     * @return 本次请求统一使用的近期口碑统计起点
     */
    public LocalDateTime getSince() { return (LocalDateTime) getAttribute("since"); }
    /**
     * 读取基础查询节点提交的店铺资料。
     *
     * @return 当前视图可见的业务值；尚未提供时为 {@code null}
     */
    public ShopBaseInfo getBase() { return (ShopBaseInfo) getAttribute("base"); }
    /**
     * 把基础资料写入上下文增量，参与引擎结果合并。
     *
     * @param value 待读取或转换的值
     */
    public void setBase(ShopBaseInfo value) { setAttribute("base", value); }
    /**
     * 记录一个分项的应用结果，失败状态不写入领域业务模型。
     *
     * @param key 上下文属性名或区域名称
     * @param result 需要转换或写入上下文的分项结果
     */
    public void put(String key, SectionResult<?> result) { setAttribute(key, result); }

    /**
     * 读取分项结果中的业务数据；节点未提交该分项时返回空值。
     *
     * @param <T> 当前业务数据类型
     * @param key 上下文属性名或区域名称
     * @return 当前视图可见的业务值；尚未提供时为 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T data(String key) {
        var value = (SectionResult<?>) getAttribute(key);
        return value == null ? null : (T) value.data();
    }

    /**
     * 执行结束后，以最终节点报告补齐未提交结果的失败或超时状态。
     *
     * @return 按区域名称组织的只读结果映射
     */
    public Map<String, SectionResult<?>> sectionResults() {
        var results = new LinkedHashMap<String, SectionResult<?>>();
        SECTION_NODES.forEach((key, nodeId) -> {
            var value = (SectionResult<?>) getAttribute(key);
            if (value == null) {
                boolean timeout = getNodeResults().stream().anyMatch(node -> node.nodeId().equals(nodeId)
                        && node.status() == NodeExecutionStatus.TIMED_OUT);
                value = SectionResult.unavailable(timeout ? "TIMED_OUT" : "FAILED",
                        timeout ? "加载超时，请重试" : "暂时不可用，请重试");
            }
            results.put(key, value);
        });
        return Map.copyOf(results);
    }
}
