package com.dianping.shopdetail.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_shop_review")
public class ShopReview {
    @TableId private Long id;
    private Long shopId, userId;
    private String requestId, content, tags;
    private Integer overallScore, tasteScore, environmentScore, serviceScore;
    private LocalDateTime createTime;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getShopId(){return shopId;} public void setShopId(Long v){shopId=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public String getTags(){return tags;} public void setTags(String v){tags=v;}
    public Integer getOverallScore(){return overallScore;} public void setOverallScore(Integer v){overallScore=v;}
    public Integer getTasteScore(){return tasteScore;} public void setTasteScore(Integer v){tasteScore=v;}
    public Integer getEnvironmentScore(){return environmentScore;} public void setEnvironmentScore(Integer v){environmentScore=v;}
    public Integer getServiceScore(){return serviceScore;} public void setServiceScore(Integer v){serviceScore=v;}
    public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
}
