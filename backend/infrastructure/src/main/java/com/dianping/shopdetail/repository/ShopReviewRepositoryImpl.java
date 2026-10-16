package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.persistence.dataobject.ShopReview;
import com.dianping.shopdetail.persistence.mapper.ShopReviewMapper;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class ShopReviewRepositoryImpl implements ShopReviewRepository {
    private final ShopReviewMapper mapper;
    public ShopReviewRepositoryImpl(ShopReviewMapper mapper) { this.mapper = mapper; }
    public boolean shopExists(Long id){ return mapper.shopExists(id); }
    public boolean lockShop(Long id){ return mapper.lockShop(id)!=null; }
    public Optional<com.dianping.shopdetail.model.ShopReview> findSubmission(Long userId,String requestId){
        return Optional.ofNullable(mapper.findSubmission(userId,requestId)).map(this::toDomain);
    }
    public Long insert(com.dianping.shopdetail.model.ShopReview review){
        ShopReview row=toRow(review); try { mapper.insert(row); return row.getId(); }
        catch (DuplicateKeyException error) { throw new com.dianping.shopdetail.exception.DuplicateReviewSubmissionException(error); }
    }
    public ReviewStatistics statistics(Long id){
        Map<String,Object> m=mapper.statistics(id); return new ReviewStatistics(number(m,"total"),number(m,"scoreSum"));
    }
    private static long number(Map<String,Object> values,String key){ return values.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).map(Number.class::cast).findFirst().orElse(0L).longValue(); }
    public void updateStatistics(Long id,ReviewStatistics s){ mapper.updateStatistics(id,s.storedScore(),Math.toIntExact(s.total())); }
    public List<com.dianping.shopdetail.model.ShopReview> page(Long id,long offset,int size){return mapper.page(id,offset,size).stream().map(this::toDomain).toList();}
    public Map<Long,ReviewAuthor> authors(Collection<Long> ids){
        if(ids.isEmpty()) return Map.of(); List<Long> list=ids.stream().toList();
        return mapper.authors(list).stream().collect(Collectors.toMap(m->numberValue(m,"id"),m->new ReviewAuthor(numberValue(m,"id"),textValue(m,"nick_name"),textValue(m,"icon"))));
    }
    private static Long numberValue(Map<String,Object> m,String key){return m.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).map(Number.class::cast).findFirst().orElseThrow().longValue();}
    private static String textValue(Map<String,Object> m,String key){return m.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).map(Object::toString).findFirst().orElse(null);}
    private com.dianping.shopdetail.model.ShopReview toDomain(ShopReview r){return new com.dianping.shopdetail.model.ShopReview(r.getId(),r.getShopId(),r.getUserId(),r.getRequestId(),r.getOverallScore(),r.getTasteScore(),r.getEnvironmentScore(),r.getServiceScore(),r.getContent(),parseTags(r.getTags()),r.getCreateTime());}
    private ShopReview toRow(com.dianping.shopdetail.model.ShopReview r){ShopReview x=new ShopReview();x.setShopId(r.shopId());x.setUserId(r.userId());x.setRequestId(r.requestId());x.setOverallScore(r.overallScore());x.setTasteScore(r.tasteScore());x.setEnvironmentScore(r.environmentScore());x.setServiceScore(r.serviceScore());x.setContent(r.content());x.setTags(toTags(r.tags()));x.setCreateTime(r.createTime());return x;}
    private static String toTags(List<String> tags){return "[\""+String.join("\",\"",tags)+"\"]";}
    private static List<String> parseTags(String value){if(value==null||value.length()<4)return List.of();return Arrays.stream(value.substring(2,value.length()-2).split("\",\"" )).filter(s->!s.isBlank()).toList();}

}
