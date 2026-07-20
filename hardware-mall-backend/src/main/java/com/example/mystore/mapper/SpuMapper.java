package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.Spu;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpuMapper extends BaseMapper<Spu> {
    @org.apache.ibatis.annotations.Update("UPDATE spu SET sales_count = sales_count + #{quantity}, update_time = NOW() WHERE id = #{spuId}")
    int incrementSalesCount(@org.apache.ibatis.annotations.Param("spuId") Long spuId, @org.apache.ibatis.annotations.Param("quantity") Integer quantity);
}
