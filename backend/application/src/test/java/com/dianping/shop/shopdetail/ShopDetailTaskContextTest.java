package com.dianping.shop.shopdetail;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import com.dianping.shared.taskchain.model.TaskNodeHolder;
import com.dianping.shared.taskchain.model.TaskNodeRegistry;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import com.dianping.shop.shopdetail.task.ShopDetailTaskContext;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过公开入口验证当前模块的业务约束和执行边界。
 */
class ShopDetailTaskContextTest {
    /**
     * 验证商详子上下文兼容共享引擎并保留请求店铺身份。
     */
    @Test
    void shopContextWorksWithTheSharedEngineAndPreservesTheRequestedShop() {
        var registry = new TaskNodeRegistry();
        var output = new ShopDetailAggregate();
        registry.register(new TaskNodeHolder<Long, ShopDetailAggregate>("assemble", "shop-context-test",
                ProcessPhase.POST_PROCESSING, 0, TaskType.CPU_BOUND, true, context -> {
                    var shop = assertInstanceOf(ShopDetailTaskContext.class, context);
                    assertEquals(7L, shop.getShopId());
                    assertEquals("预置数据", shop.getAttribute("seed"));
                    shop.setOutput(output);
                }));
        registry.freeze();
        var root = new ShopDetailTaskContext(7L);
        root.setAttribute("seed", "预置数据");
        try (var pool = Executors.newFixedThreadPool(1)) {
            ShopDetailTaskContext result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(3))
                    .executeChain("shop-context-test", root);
            assertSame(root, result);
            assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
            assertSame(output, result.getOutput());
            assertThrows(IllegalStateException.class, () -> result.setOutput(new ShopDetailAggregate()));
        }
    }
}
