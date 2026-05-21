package com.example.mystore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mystore.entity.db.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}