package com.example.mystore.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.result.Result;

import com.example.mystore.entity.db.*;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.service.SkuService;
import com.example.mystore.service.SpecItemService;
import com.example.mystore.service.SpecTemplateService;
import com.example.mystore.service.SpuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class ProductController {


    private final SpuService spuService;
    private final SkuService skuService;
    private final SpecTemplateService specTemplateService;
    private final SpecItemService specItemService;

//    @GetMapping("/product/list")
//    public Result<Page<Spu>> getProductList(
//            @RequestParam(required = false) Long categoryId,
//            @RequestParam(required = false) String keyword,
//            @RequestParam(defaultValue = "1") Integer page,
//            @RequestParam(defaultValue = "20") Integer limit) {
//        return Result.success(spuService.getSpuPage(categoryId, keyword, page, limit, 1));
//    }

//    @GetMapping("/product/recommend")
//    public Result<List<Spu>> getRecommendProducts() {
//        return Result.success(spuService.getRecommendSpus());
//    }

//    @GetMapping("/product/{id}")
//    public Result<ProductDetailVO> getProductDetail(@PathVariable Long id) {
//        Spu spu = spuService.getSpuById(id);
//        List<Sku> skus = skuService.getSkusBySpu(id);
//        List<SpecTemplate> specTemplates = specTemplateService.getTemplatesByCategory(spu.getCategoryId());
//
//        ProductDetailVO vo = new ProductDetailVO();
//        vo.setSpu(spu);
//        vo.setSkus(skus);
//        vo.setSpecTemplates(specTemplates);
//
//        Map<Long, List<SpecItem>> specItemsMap = new HashMap<>();
//        for (SpecTemplate template : specTemplates) {
//            List<SpecItem> items = specItemService.getItemsByTemplate(template.getId());
//            specItemsMap.put(template.getId(), items);
//        }
//        vo.setSpecItemsMap(specItemsMap);
//
//        return Result.success(vo);
//    }

//    @GetMapping("/product/{id}/skus")
//    public Result<List<Sku>> getProductSkus(@PathVariable Long id) {
//        return Result.success(skuService.getSkusBySpu(id));
//    }

//    @GetMapping("/product/{id}/specs")
//    public Result<List<SpecTemplate>> getProductSpecs(@PathVariable Long id) {
//        Spu spu = spuService.getSpuById(id);
//        return Result.success(specTemplateService.getTemplatesByCategory(spu.getCategoryId()));
//    }
}
