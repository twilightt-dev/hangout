package com.dianping.shopdetail.persistence.mapper;

import com.dianping.shopdetail.persistence.dataobject.ShopSetmeal;
import com.dianping.shopdetail.persistence.dataobject.ShopSetmealItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 套餐展示所需的只读查询，明细按套餐集合批量读取。
 */
public interface ShopSetmealMapper {
    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id = #{shopId}")
    boolean shopExists(Long shopId);

    /**
     * 按展示顺序读取当前店铺的上架套餐主记录。
     *
     * @param shopId 店铺标识
     * @return 有序上架套餐主记录列表
     */
    @Select("SELECT * FROM tb_shop_setmeal WHERE shop_id = #{shopId} AND status = 1 ORDER BY sort ASC, id ASC")
    List<ShopSetmeal> findPublished(Long shopId);

    /**
     * 统计当前店铺上架套餐总数。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    @Select("SELECT COUNT(*) FROM tb_shop_setmeal WHERE shop_id = #{shopId} AND status = 1")
    long countPublished(Long shopId);

    /**
     * 按展示顺序分页读取上架套餐。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Select("SELECT * FROM tb_shop_setmeal WHERE shop_id = #{shopId} AND status = 1 ORDER BY sort ASC, id ASC LIMIT #{size} OFFSET #{offset}")
    List<ShopSetmeal> pagePublished(Long shopId, long offset, int size);

    /**
     * 批量读取指定套餐的菜品明细，避免逐套餐查询。
     *
     * @param ids 待批量查询的记录标识
     * @return 按套餐和展示顺序排列的固定菜品明细
     */
    @Select("""
            <script>
            SELECT * FROM tb_shop_setmeal_item WHERE setmeal_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY sort ASC, id ASC
            </script>
            """)
    List<ShopSetmealItem> findItems(@Param("ids") List<Long> ids);
}
