package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.persistence.dataobject.ShopReview;
import com.dianping.shopdetail.persistence.mapper.ShopReviewMapper;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import com.dianping.shopdetail.model.ShopPage;
import com.dianping.shopdetail.model.ShopReviewSummary;
import org.springframework.transaction.annotation.*;

/**
 * 实现ShopReview仓储，负责数据库查询及持久化模型转换。
 */
@Repository
public class ShopReviewRepositoryImpl implements ShopReviewRepository {
    /**
     * 数据映射器。
     */
    private final ShopReviewMapper mapper;
    /**
     * 创建 ShopReviewRepositoryImpl，初始化所需依赖及实例状态。
     *
     * @param mapper 当前业务的 MyBatis 数据访问接口
     */
    public ShopReviewRepositoryImpl(ShopReviewMapper mapper) { this.mapper = mapper; }
    /**
     * 检查店铺记录是否存在。
     *
     * @param id 记录标识
     * @return 店铺是否存在
     */
    public boolean shopExists(Long id){ return mapper.shopExists(id); }
    /**
     * 锁定店铺行，串行化该店铺的业务写入。
     *
     * @param id 记录标识
     * @return 店铺是否存在且已成功取得行锁
     */
    public boolean lockShop(Long id){ return mapper.lockShop(id)!=null; }
    /**
     * 按作者和提交标识查询已持久化内容，用于幂等处理。
     *
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @return 相同用户及提交标识对应的持久化记录；不存在时为空
     */
    public Optional<com.dianping.shopdetail.model.ShopReview> findSubmission(Long userId,String requestId){
        return Optional.ofNullable(mapper.findSubmission(userId,requestId)).map(this::toDomain);
    }
    /**
     * 插入经过领域校验的业务记录，并返回生成标识。
     *
     * @param review 评价业务数据
     * @return 新记录标识
     */
    public Long insert(com.dianping.shopdetail.model.ShopReview review){
        ShopReview row=toRow(review); try { mapper.insert(row); return row.getId(); }
        catch (DuplicateKeyException error) { throw new com.dianping.shopdetail.exception.DuplicateReviewSubmissionException(error); }
    }
    /**
     * 统计指定店铺的全部业务记录。
     *
     * @param id 记录标识
     * @return 匹配记录数或十倍总体评分总和
     */
    public long countByShopId(Long id){ return mapper.countByShopId(id); }
    /**
     * 汇总店铺全部评价的十倍总体评分。
     *
     * @param id 记录标识
     * @return 匹配记录数或十倍总体评分总和
     */
    public long sumOverallScoreByShopId(Long id){ return mapper.sumOverallScoreByShopId(id); }
    /**
     * 更新店铺评分与评价数；须与评价写入处于同一事务。
     *
     * @param id 记录标识
     * @param score 按实际分数的十倍保存的评分
     * @param total 全部匹配记录数
     */
    public void updateStatistics(Long id,int score,int total){ mapper.updateStatistics(id,score,total); }
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param id 记录标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    public List<com.dianping.shopdetail.model.ShopReview> page(Long id,long offset,int size){return mapper.page(id,offset,size).stream().map(this::toDomain).toList();}
    /**
     * 在同一读取快照内取得分页明细和总数。
     *
     * @param id 记录标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopPage<com.dianping.shopdetail.model.ShopReview> pageWithTotal(Long id, long current, int size) {
        return new ShopPage<>(page(id, Math.multiplyExact(current - 1, (long) size), size), countByShopId(id), current, size);
    }
    /**
     * 在同一读取快照内汇总全店评价数量及均分。
     *
     * @param id 记录标识
     * @return 全店评价数量和五分制总体均分；无评价时均分为空
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopReviewSummary summarize(Long id) {
        return ShopReviewSummary.fromTotals(countByShopId(id), sumOverallScoreByShopId(id));
    }
    /**
     * 将数据库记录映射为领域业务数据。
     *
     * @param r 待转换的数据库或领域记录
     * @return 由持久化字段恢复的领域业务对象
     */
    private com.dianping.shopdetail.model.ShopReview toDomain(ShopReview r){return new com.dianping.shopdetail.model.ShopReview(r.getId(),r.getShopId(),r.getUserId(),r.getRequestId(),r.getOverallScore(),r.getTasteScore(),r.getEnvironmentScore(),r.getServiceScore(),r.getContent(),parseTags(r.getTags()),r.getCreateTime(),r.getUserName(),r.getUserIcon());}
    /**
     * 将领域业务数据映射为待写入数据库的记录。
     *
     * @param r 待转换的数据库或领域记录
     * @return 可交由 MyBatis 写入的持久化记录
     */
    private ShopReview toRow(com.dianping.shopdetail.model.ShopReview r){ShopReview x=new ShopReview();x.setShopId(r.shopId());x.setUserId(r.userId());x.setRequestId(r.requestId());x.setOverallScore(r.overallScore());x.setTasteScore(r.tasteScore());x.setEnvironmentScore(r.environmentScore());x.setServiceScore(r.serviceScore());x.setContent(r.content());x.setTags(toTags(r.tags()));x.setCreateTime(r.createTime());return x;}
    /**
     * 将已规范化的预设标签序列化为数据库 JSON 文本。
     *
     * @param tags 用户选择的预设标签
     * @return 可存入数据库的标签 JSON 文本
     */
    private static String toTags(List<String> tags){return "[\""+String.join("\",\"",tags)+"\"]";}
    /**
     * 将数据库保存的预设标签 JSON 文本还原为标签列表。
     *
     * @param value 待读取或转换的值
     * @return 从存储文本还原的预设标签列表
     */
    private static List<String> parseTags(String value){if(value==null||value.length()<4)return List.of();return Arrays.stream(value.substring(2,value.length()-2).split("\",\"" )).filter(s->!s.isBlank()).toList();}

}
