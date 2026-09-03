package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.mapper.LogisticsMapper;
import com.example.mystore.service.LogisticsService;
import com.example.mystore.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    private final LogisticsMapper logisticsMapper;
    private final RedisUtil redisUtil;

    @Override
    public List<Logistics> getEnabledLogistics() {
        Object cached = redisUtil.get(RedisConstants.PREFIX_LOGISTICS_ENABLED);
        if (cached != null) {
            return (List<Logistics>) cached;
        }
        LambdaQueryWrapper<Logistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Logistics::getStatus, 1)
               .eq(Logistics::getDeleteTime, 0)
               .orderByDesc(Logistics::getSortOrder);
        List<Logistics> list = logisticsMapper.selectList(wrapper);
        redisUtil.setWithJitter(RedisConstants.PREFIX_LOGISTICS_ENABLED, list, RedisConstants.CACHE_TTL_HOUR, TimeUnit.SECONDS, RedisConstants.CACHE_JITTER_MAX);
        return list;
    }

    @Override
    public List<Logistics> getAllLogistics() {
        LambdaQueryWrapper<Logistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Logistics::getDeleteTime, 0)
               .orderByDesc(Logistics::getSortOrder);
        return logisticsMapper.selectList(wrapper);
    }

    @Override
    public Page<Logistics> getLogisticsPage(Integer page, Integer limit, String name, String city, Integer status) {
        Page<Logistics> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<Logistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Logistics::getDeleteTime, 0);
        if (StringUtils.hasText(name)) {
            wrapper.like(Logistics::getName, name);
        }
        if (StringUtils.hasText(city)) {
            wrapper.eq(Logistics::getCity, city);
        }
        if (status != null) {
            wrapper.eq(Logistics::getStatus, status);
        }
        wrapper.orderByDesc(Logistics::getSortOrder);
        return logisticsMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Logistics getLogisticsById(Long id) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null || logistics.getDeleteTime() != 0) {
            return null;
        }
        return logistics;
    }

    @Override
    public Logistics createLogistics(Logistics logistics) {
        logistics.setCreateTime(LocalDateTime.now());
        logistics.setUpdateTime(LocalDateTime.now());
        logistics.setDeleteTime(0L);
        logisticsMapper.insert(logistics);
        redisUtil.delete(RedisConstants.PREFIX_LOGISTICS_ENABLED);
        return logistics;
    }

    @Override
    public Logistics updateLogistics(Logistics logistics) {
        Logistics exist = logisticsMapper.selectById(logistics.getId());
        if (exist == null) {
            throw new BusinessException("物流方式不存在");
        }

        if (StringUtils.hasText(logistics.getName())) {
            exist.setName(logistics.getName());
        }
        if (StringUtils.hasText(logistics.getCode())) {
            exist.setCode(logistics.getCode());
        }
        if (logistics.getDescription() != null) {
            exist.setDescription(logistics.getDescription());
        }
        if (logistics.getContact() != null) {
            exist.setContact(logistics.getContact());
        }
        if (logistics.getPhones() != null) {
            exist.setPhones(logistics.getPhones());
        }
        if (logistics.getAddress() != null) {
            exist.setAddress(logistics.getAddress());
        }
        if (StringUtils.hasText(logistics.getCity())) {
            exist.setCity(logistics.getCity());
        }
        if (logistics.getSortOrder() != null) {
            exist.setSortOrder(logistics.getSortOrder());
        }
        if (logistics.getStatus() != null) {
            exist.setStatus(logistics.getStatus());
        }
        exist.setUpdateTime(LocalDateTime.now());

        logisticsMapper.updateById(exist);
        redisUtil.delete(RedisConstants.PREFIX_LOGISTICS_ENABLED);
        return exist;
    }

    @Override
    public void deleteLogistics(Long id) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException("物流公司不存在");
        }
        logistics.setDeleteTime(System.currentTimeMillis());
        logisticsMapper.updateById(logistics);
        redisUtil.delete(RedisConstants.PREFIX_LOGISTICS_ENABLED);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Logistics logistics = logisticsMapper.selectById(id);
        if (logistics == null) {
            throw new BusinessException("物流方式不存在");
        }
        logistics.setStatus(status);
        logistics.setUpdateTime(LocalDateTime.now());
        logisticsMapper.updateById(logistics);
        redisUtil.delete(RedisConstants.PREFIX_LOGISTICS_ENABLED);
    }
}
