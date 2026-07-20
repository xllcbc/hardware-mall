package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.Spu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpuMapper extends BaseMapper<Spu> {

    @Update("UPDATE spu SET sales_count = sales_count + #{quantity}, update_time = NOW() WHERE id = #{spuId}")
    int incrementSalesCount(@Param("spuId") Long spuId, @Param("quantity") Integer quantity);
}
