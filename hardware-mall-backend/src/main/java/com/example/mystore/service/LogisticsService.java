package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.Logistics;

import java.util.List;

public interface LogisticsService {
    List<Logistics> getEnabledLogistics();
    List<Logistics> getAllLogistics();
    Page<Logistics> getLogisticsPage(Integer page, Integer limit, String name, String city, Integer status);
    Logistics getLogisticsById(Long id);
    Logistics createLogistics(Logistics logistics);
    Logistics updateLogistics(Logistics logistics);
    void deleteLogistics(Long id);
    void updateStatus(Long id, Integer status);
}
