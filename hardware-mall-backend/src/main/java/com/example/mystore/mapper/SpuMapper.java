package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.Spu;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SpuMapper extends BaseMapper<Spu> {
}
