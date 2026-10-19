package com.dianping.shopdetail.persistence.mapper;

import com.dianping.shopdetail.persistence.dataobject.ShopSetmeal;
import com.dianping.shopdetail.persistence.dataobject.ShopSetmealItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** 套餐展示所需的只读查询，明细按套餐集合批量读取。 */
public interface ShopSetmealMapper {
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id = #{shopId}")
    boolean shopExists(Long shopId);

    @Select("SELECT * FROM tb_shop_setmeal WHERE shop_id = #{shopId} AND status = 1 ORDER BY sort ASC, id ASC")
    List<ShopSetmeal> findPublished(Long shopId);

    @Select("""
            <script>
            SELECT * FROM tb_shop_setmeal_item WHERE setmeal_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY sort ASC, id ASC
            </script>
            """)
    List<ShopSetmealItem> findItems(@Param("ids") List<Long> ids);
}
