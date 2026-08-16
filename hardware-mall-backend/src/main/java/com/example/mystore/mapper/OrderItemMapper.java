package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Insert("<script>" +
            "INSERT INTO order_item(order_id, sku_id, spu_id, product_name, product_spec, product_image, price, quantity, subtotal, create_time) " +
            "VALUES " +
            "<foreach collection='items' item='it' separator=','>" +
            "(#{it.orderId},#{it.skuId},#{it.spuId},#{it.productName},#{it.productSpec},#{it.productImage},#{it.price},#{it.quantity},#{it.subtotal},#{it.createTime})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("items") List<OrderItem> items);
}
