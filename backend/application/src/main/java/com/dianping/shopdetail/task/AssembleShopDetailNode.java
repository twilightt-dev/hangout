package com.dianping.shopdetail.task;
import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shopdetail.model.*;
import org.springframework.stereotype.Component;
/** 先提交基础组合，保证可选增强失败时仍有有效输出。 */
@Component
@TaskNode(nodeId="assemble", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.POST_PROCESSING,
        order=10, taskType=TaskType.CPU_BOUND, required=true)
public class AssembleShopDetailNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        if (context.getBase() == null) throw new IllegalStateException("基础查询未生成店铺资料");
        context.setOutput(ShopDetailAggregate.builder().shop(context.getBase())
                .vouchers(context.<ShopPage<ShopVoucher>>section("vouchers", "vouchers").data())
                .setmeals(context.<ShopPage<ShopSetmeal>>section("setmeals", "setmeals").data())
                .recommendations(context.<ShopPage<RecommendDish>>section("recommendations", "recommendations").data())
                .reviews(context.<ShopPage<ShopReview>>section("reviews", "reviews").data())
                .reviewSummary(context.<ShopReviewSummary>section("statistics", "reviews").data())
                .blogs(context.<ShopPage<ShopVisitBlog>>section("blogs", "blogs").data()).build());
    }
}
