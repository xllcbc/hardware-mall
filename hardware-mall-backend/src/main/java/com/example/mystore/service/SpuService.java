package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.vo.ProductListVO;
import com.example.mystore.entity.vo.ProductDetailVO;
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

    Page<ProductListVO> getProductListVO(Long categoryId, String keyword, Integer page, Integer limit, Integer status);
    List<ProductListVO> getRecommendProductListVO(Integer limit);
    ProductDetailVO getProductDetailVO(Long id);
}
