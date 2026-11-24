package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.AdminProductPricingVO;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.ProductDetailVO;
import com.example.mystore.entity.vo.ProductSkuVO;
import com.example.mystore.entity.vo.UserLevelProductPreviewVO;
import java.time.LocalDateTime;
import java.util.List;

public interface SpuService {
    Page<Spu> getSpuPage(Long categoryId, String keyword, Integer page, Integer limit, Integer status);
    List<Spu> getRecommendSpus();
    Spu getSpuById(Long id);
    Spu createSpu(Spu spu);
    Spu updateSpu(Spu spu);
    void deleteSpu(Long id);
    void updateStatus(Long id, Integer status);
    Long getTotalCount();
    /** M11: 区间新建商品数(上新量统计, 含未上架) */
    Long countCreatedBetween(LocalDateTime start, LocalDateTime end);

    Page<ProductListVO> getProductListVO(Long categoryId, String keyword, Integer page, Integer limit, Integer status);
    List<ProductListVO> getRecommendProductListVO(Integer limit);
    /** 按商品 ID 批量取当前等级结算价(收藏/足迹等本地价格快照刷新用)。 */
    List<ProductListVO> getProductPriceList(List<Long> ids);
    ProductDetailVO getProductDetailVO(Long id);
    List<ProductSkuVO> getProductSkuVOs(Long spuId);
    Page<AdminProductPricingVO> getAdminProductPricingPage(
            Long levelId, Long categoryId, String keyword, Integer status, Integer page, Integer limit);
    Page<UserLevelProductPreviewVO> getUserLevelProductPreview(
            Long levelId, Long categoryId, Integer page, Integer limit);
}
