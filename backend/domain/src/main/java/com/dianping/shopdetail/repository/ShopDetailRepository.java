package com.dianping.shopdetail.repository;
import com.dianping.shopdetail.model.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/** 商详基础、探店、语料和周边的读取边界，不依赖旧业务 Service。 */
public interface ShopDetailRepository {
    Optional<ShopBaseInfo> findShop(Long shopId);
    ShopPage<ShopVisitBlog> blogs(Long shopId, long current, int size, Long viewerId);
    List<TagEvidence> reviewEvidence(Long shopId, LocalDateTime since, int limit);
    List<TagEvidence> blogEvidence(Long shopId, LocalDateTime since, int limit);
    ShopPage<ShopRelatedShop> related(ShopBaseInfo center, long current, int size);
}
