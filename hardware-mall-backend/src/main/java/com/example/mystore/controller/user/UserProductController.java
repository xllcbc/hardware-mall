package com.example.mystore.controller.user;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.service.SpuService;
import com.example.mystore.service.SkuService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/product")
@RequiredArgsConstructor
public class UserProductController {

    private final SpuService spuService;
    private final SkuService skuService;
    private final SpecTemplateService specTemplateService;

    @GetMapping("/list")
    public Result<Page<ProductListVO>> getProductList(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(spuService.getProductListVO(categoryId, keyword, page, limit, 1));
    }



    @GetMapping("/recommend")
    public Result<List<ProductListVO>> getRecommendProducts(
            @RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(spuService.getRecommendProductListVO(limit));
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVO> getProductDetail(@PathVariable Long id) {
        return Result.success(spuService.getProductDetailVO(id));
    }

    @GetMapping("/{id}/skus")
    public Result<List<Sku>> getProductSkus(@PathVariable Long id) {
        return Result.success(skuService.getSkusBySpu(id));
    }

    @GetMapping("/{id}/specs")
    public Result<List<SpecTemplate>> getProductSpecs(@PathVariable Long id) {
        Spu spu = spuService.getSpuById(id);
        return Result.success(specTemplateService.getTemplatesByCategory(spu.getCategoryId()));
    }
}