package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.annotation.RequireAdmin;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.dto.CopyLevelRateRequest;
import com.example.mystore.entity.dto.LevelRateBatchRequest;
import com.example.mystore.entity.dto.RateRequest;
import com.example.mystore.entity.vo.LevelRateVO;
import com.example.mystore.entity.vo.UserLevelProductPreviewVO;
import com.example.mystore.entity.vo.UserLevelVO;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.SpuService;
import com.example.mystore.service.UserLevelService;
import com.example.mystore.util.PageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user-level")
@RequiredArgsConstructor
@RequireAdmin
public class AdminUserLevelController {

    private final UserLevelService userLevelService;
    private final PricingRuleService pricingRuleService;
    private final SpuService spuService;

    @GetMapping("/list")
    public Result<List<UserLevelVO>> list() {
        return Result.success(userLevelService.list());
    }

    @PostMapping
    public Result<UserLevel> create(@RequestBody UserLevel level) {
        return Result.success(userLevelService.create(level));
    }

    @PutMapping("/{id}")
    public Result<UserLevel> update(@PathVariable Long id, @RequestBody UserLevel level) {
        return Result.success(userLevelService.update(id, level));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userLevelService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userLevelService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/rates")
    public Result<List<LevelRateVO>> getRates(@PathVariable Long id) {
        return Result.success(pricingRuleService.getRates(id));
    }

    @PutMapping("/{id}/rates")
    public Result<Void> saveRates(
            @PathVariable Long id,
            @RequestBody LevelRateBatchRequest request) {
        pricingRuleService.saveRates(id, request == null ? null : request.getItems());
        return Result.success();
    }

    @PostMapping("/{id}/rates/fill")
    public Result<Void> fillRates(@PathVariable Long id, @RequestBody RateRequest request) {
        pricingRuleService.fillRates(id, request == null ? null : request.getRate());
        return Result.success();
    }

    @PostMapping("/{id}/rates/copy")
    public Result<Void> copyRates(
            @PathVariable Long id,
            @RequestBody CopyLevelRateRequest request) {
        pricingRuleService.copyRates(id, request == null ? null : request.getSourceLevelId());
        return Result.success();
    }

    @GetMapping("/{id}/product-preview")
    public Result<Page<UserLevelProductPreviewVO>> preview(
            @PathVariable Long id,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        userLevelService.getById(id);
        return Result.success(spuService.getUserLevelProductPreview(
                id, categoryId, page, PageUtil.clampLimit(limit, 20, 50)));
    }
}
