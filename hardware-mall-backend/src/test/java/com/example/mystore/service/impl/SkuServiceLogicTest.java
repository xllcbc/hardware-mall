package com.example.mystore.service.impl;

import com.example.mystore.entity.vo.SpecVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkuServiceImpl 纯逻辑方法测试（通过反射测试私有方法）
 */
class SkuServiceLogicTest {

    @Test
    void testComputeSpecHash_Consistency() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("computeSpecHash", List.class);
        method.setAccessible(true);

        SpecVO spec1 = new SpecVO();
        spec1.setTemplateId(1L);
        spec1.setItemId(1L);
        SpecVO spec2 = new SpecVO();
        spec2.setTemplateId(2L);
        spec2.setItemId(4L);

        String hash1 = (String) method.invoke(service, Arrays.asList(spec1, spec2));
        String hash2 = (String) method.invoke(service, Arrays.asList(spec2, spec1)); // 顺序不同

        // 相同规格组合，不同顺序，哈希值应一致
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEmpty();
    }

    @Test
    void testComputeSpecHash_DifferentSpecs() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("computeSpecHash", List.class);
        method.setAccessible(true);

        SpecVO spec1 = new SpecVO();
        spec1.setTemplateId(1L);
        spec1.setItemId(1L);

        SpecVO spec2 = new SpecVO();
        spec2.setTemplateId(1L);
        spec2.setItemId(2L);

        String hash1 = (String) method.invoke(service, Arrays.asList(spec1));
        String hash2 = (String) method.invoke(service, Arrays.asList(spec2));

        // 不同规格组合，哈希值应不同
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void testCartesianProduct_2x2() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("cartesianProduct", List.class);
        method.setAccessible(true);

        List<List<String>> lists = Arrays.asList(
                Arrays.asList("A", "B"),
                Arrays.asList("1", "2")
        );

        @SuppressWarnings("unchecked")
        List<List<String>> result = (List<List<String>>) method.invoke(service, lists);

        assertThat(result).hasSize(4);
        assertThat(result).containsExactlyInAnyOrder(
                Arrays.asList("A", "1"),
                Arrays.asList("A", "2"),
                Arrays.asList("B", "1"),
                Arrays.asList("B", "2")
        );
    }

    @Test
    void testCartesianProduct_3x2() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("cartesianProduct", List.class);
        method.setAccessible(true);

        List<List<String>> lists = Arrays.asList(
                Arrays.asList("A", "B", "C"),
                Arrays.asList("1", "2")
        );

        @SuppressWarnings("unchecked")
        List<List<String>> result = (List<List<String>>) method.invoke(service, lists);

        assertThat(result).hasSize(6);
    }

    @Test
    void testCartesianProduct_Empty() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("cartesianProduct", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<List<String>> result = (List<List<String>>) method.invoke(service, new ArrayList<List<String>>());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEmpty();
    }
}
