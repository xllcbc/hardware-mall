package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.service.LogisticsService;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/logistics")
@RequiredArgsConstructor
@RequireAdmin
public class AdminLogisticsController {

    private final LogisticsService logisticsService;

    @GetMapping("/list")
    public Result<Page<Logistics>> getLogisticsList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer status) {
        return Result.success(logisticsService.getLogisticsPage(page, limit, name, city, status));
    }

    @GetMapping("/{id}")
    public Result<Logistics> getLogisticsById(@PathVariable Long id) {
        return Result.success(logisticsService.getLogisticsById(id));
    }

    @PostMapping
    public Result<Logistics> createLogistics(@RequestBody Logistics logistics) {
        return Result.success(logisticsService.createLogistics(logistics));
    }

    @PutMapping("/{id}")
    public Result<Logistics> updateLogistics(@PathVariable Long id, @RequestBody Logistics logistics) {
        logistics.setId(id);
        return Result.success(logisticsService.updateLogistics(logistics));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteLogistics(@PathVariable Long id) {
        logisticsService.deleteLogistics(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        logisticsService.updateStatus(id, status);
        return Result.success();
    }
}
