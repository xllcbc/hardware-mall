package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.Cart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {

    @Update("INSERT INTO cart (user_id, sku_id, quantity, create_time, update_time, delete_time) " +
            "VALUES (#{userId}, #{skuId}, #{quantity}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE " +
            "quantity = IF(delete_time = 0, quantity + VALUES(quantity), VALUES(quantity)), " +
            "update_time = NOW(), " +
            "delete_time = 0")
    int insertOrUpdateQuantity(@Param("userId") Long userId,
                               @Param("skuId") Long skuId,
                               @Param("quantity") Integer quantity);
}
