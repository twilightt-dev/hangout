package com.dianping.shop.shopdetail.service;

import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.PhaseConfig;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.task.ShopDetailTaskContext;
import com.dianping.shopdetail.exception.ShopDetailException;
import com.dianping.shopdetail.model.ShopDetailAggregate;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 商详首次展示用例：选择执行路径、编排任务并检查执行结果。
 */
@Service
public class ShopDetailApplicationService {
    /**
     * 共享任务链引擎；允许未装配。
     */
    private final TaskChainEngine engine;
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 商详任务链阶段的执行配置。
     */
    private final List<PhaseConfig> phases;

    /**
     * 创建 ShopDetailApplicationService，初始化所需依赖及实例状态。
     * @param engine 共享任务链引擎；允许未装配
     * @param domain 商详领域服务
     * @param queryTimeout 并行查询阶段总预算，单位为毫秒
     * @param postTimeout 后处理阶段共享预算，单位为毫秒
     */
    public ShopDetailApplicationService(@Nullable TaskChainEngine engine, ShopDetailDomainService domain,
            @Value("${shop-detail.query-timeout-ms:2000}") long queryTimeout,
            @Value("${shop-detail.post-timeout-ms:1000}") long postTimeout) {
        this.engine = engine;
        this.domain = domain;
        phases = List.of(
                new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.PARALLEL, Duration.ofMillis(queryTimeout)),
                new PhaseConfig(ProcessPhase.POST_PROCESSING, ExecutionMode.SERIAL, Duration.ofMillis(postTimeout)));
    }

    /**
     * 选择任务链或同步聚合路径，检查必需结果并转换六区响应及对外错误
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 包含六个展示区域及各分项状态的商详响应
     * @throws ShopDetailException 输入非法、店铺不存在，或必需查询失败、超时
     */
    public ShopDetailResult getShopDetail(Long shopId, Long viewerId) {
        if (shopId == null || shopId <= 0) throw new ShopDetailException(400, "店铺 ID 必须为正整数");
        ShopDetailAggregate detail;
        if (engine == null) {
            try {
                detail = domain.getShopDetail(shopId, viewerId);
            } catch (RuntimeException error) {
                throw failure(shopId, error);
            }
        } else {
            var context = engine.executePhaseChain(ShopDetailTaskContext.GROUP,
                    new ShopDetailTaskContext(shopId, viewerId), phases);
            ensureSuccess(context);
            return ShopDetailResult.from(context.getOutput(), context.sectionResults());
        }
        return ShopDetailResult.from(detail);
    }

    /**
     * 校验必需任务执行状态与输出，转换为商详用例错误。
     *
     * @param context 当前调用独立使用的任务上下文
     */
    private void ensureSuccess(ShopDetailTaskContext context) {
        if (!context.isSuccess()) {
            throw failure(context.getShopId(), context.getError());
        }
        if (context.getOutput() == null) throw new ShopDetailException(503, "商详未生成有效结果");
    }

    /**
     * 两种执行路径使用相同的用例异常边界，不暴露底层错误。
     *
     * @param shopId 店铺标识
     * @param error 导致失败的异常；没有异常时为 {@code null}
     * @return 保留已知业务状态或转换为 503、504 的商详异常
     */
    private ShopDetailException failure(Long shopId, Throwable error) {
        org.apache.commons.logging.LogFactory.getLog(getClass()).error("商详必需查询失败，shopId=" + shopId, error);
        if (error instanceof ShopDetailException known) return known;
        return new ShopDetailException(error instanceof TimeoutException ? 504 : 503,
                "商详暂时不可用，请稍后重试", error);
    }
}
