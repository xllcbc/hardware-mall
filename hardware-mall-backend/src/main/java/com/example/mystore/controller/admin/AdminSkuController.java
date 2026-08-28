package com.example.mystore.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.service.SkuService;
import com.example.mystore.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sku")
@RequiredArgsConstructor
@RequireAdmin
public class AdminSkuController {

    private final SkuService skuService;

    @GetMapping("/list")
    public Result<Page<Sku>> getSkuList(
            @RequestParam Long spuId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(skuService.getSkuPage(spuId, page, limit));
    }

    @GetMapping("/{id}")
    public Result<Sku> getSkuById(@PathVariable Long id) {
        return Result.success(skuService.getSkuById(id));
    }

    @GetMapping("/spu/{spuId}")
    public Result<List<Sku>> getSkusBySpu(
            @PathVariable Long spuId,
            @RequestParam(required = false) Integer status) {
        return Result.success(skuService.getSkusBySpu(spuId, status));
    }

    @GetMapping("/counts")
    public Result<Map<Long, Long>> getSkuCounts(@RequestParam List<Long> spuIds) {
        return Result.success(skuService.countBySpuIds(spuIds));
    }

    @PostMapping
    public Result<Sku> createSku(@RequestBody Sku sku) {
        return Result.success(skuService.createSku(sku));
    }

    @PutMapping("/{id}")
    public Result<Sku> updateSku(@PathVariable Long id, @RequestBody Sku sku) {
        sku.setId(id);
        return Result.success(skuService.updateSku(sku));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSku(@PathVariable Long id) {
        skuService.deleteSku(id);
        return Result.success();
    }

    @PostMapping("/generate/{spuId}")
    public Result<List<Sku>> generateSkus(@PathVariable Long spuId) {
        return Result.success(skuService.previewSkusByTemplate(spuId));
    }
}
