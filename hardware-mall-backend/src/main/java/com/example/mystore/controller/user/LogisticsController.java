package com.example.mystore.controller.user;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Logistics;
import com.example.mystore.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService logisticsService;

    @GetMapping("/list")
    public Result<List<Logistics>> getLogisticsList() {
        return Result.success(logisticsService.getEnabledLogistics());
    }
}
