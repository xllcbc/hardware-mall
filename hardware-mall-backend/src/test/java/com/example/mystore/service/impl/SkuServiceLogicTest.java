package com.example.mystore.service.impl;

import com.example.mystore.entity.vo.SpecVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkuServiceImpl 纯逻辑方法测试（通过反射测试私有方法）
 */
class SkuServiceLogicTest {

    @Test
    void testComputeSpecHash_Consistency() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
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
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
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

    private static SpecVO spec(Long templateId, Long itemId) {
        SpecVO vo = new SpecVO();
        vo.setTemplateId(templateId);
        vo.setItemId(itemId);
        return vo;
    }

    @Test
    void testNormalizeSpecKey_IgnoresNameAndValue() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("normalizeSpecKey", List.class);
        method.setAccessible(true);

        SpecVO withLabels = spec(1L, 1L);
        withLabels.setName("颜色");
        withLabels.setValue("黑");

        // name/value 只是展示文案，不参与规格组合身份判定
        assertThat((String) method.invoke(service, Arrays.asList(withLabels)))
                .isEqualTo((String) method.invoke(service, Arrays.asList(spec(1L, 1L))));
    }

    @Test
    void testNormalizeSpecKey_NullTemplateId() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("normalizeSpecKey", List.class);
        method.setAccessible(true);

        // 模拟 seed "默认" SKU：specs 无 templateId/itemId
        SpecVO defaultSpec = new SpecVO();
        defaultSpec.setName("颜色");
        defaultSpec.setValue("默认");

        assertThat((String) method.invoke(service, Arrays.asList(defaultSpec))).isEqualTo("default");
        assertThat((String) method.invoke(service, Arrays.asList(spec(1L, 2L), defaultSpec)))
                .isEqualTo("1:2|default");
    }

    @Test
    void testNormalizeSpecKey_OrderInsensitive() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("normalizeSpecKey", List.class);
        method.setAccessible(true);

        // 同 templateId 不同 itemId 的乱序（旧实现仅按 templateId 排序，结果不稳定）
        assertThat((String) method.invoke(service, Arrays.asList(spec(1L, 1L), spec(1L, 2L))))
                .isEqualTo((String) method.invoke(service, Arrays.asList(spec(1L, 2L), spec(1L, 1L))));

        assertThat((String) method.invoke(service, Arrays.asList(spec(1L, 1L), spec(2L, 4L))))
                .isEqualTo((String) method.invoke(service, Arrays.asList(spec(2L, 4L), spec(1L, 1L))));
    }

    @Test
    void testNormalizeSpecKey_DistinctCombos() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("normalizeSpecKey", List.class);
        method.setAccessible(true);

        String key11 = (String) method.invoke(service, Arrays.asList(spec(1L, 1L)));

        assertThat(key11).isNotEqualTo((String) method.invoke(service, Arrays.asList(spec(1L, 2L))));
        assertThat(key11).isNotEqualTo((String) method.invoke(service, Arrays.asList(spec(2L, 1L))));
        assertThat(key11).isNotEqualTo(
                (String) method.invoke(service, Arrays.asList(spec(1L, 1L), spec(1L, 1L))));
        assertThat((String) method.invoke(service, new Object[]{null})).isEqualTo("");
        assertThat((String) method.invoke(service, Collections.emptyList())).isEqualTo("");
    }

    @Test
    void testComputeSpecHash_Md5HexFormat() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("computeSpecHash", List.class);
        method.setAccessible(true);

        String hash = (String) method.invoke(service, Arrays.asList(spec(1L, 1L)));
        assertThat(hash).hasSize(32).matches("[0-9a-f]{32}");
        assertThat((String) method.invoke(service, Arrays.asList(spec(1L, 1L)))).isEqualTo(hash);
        assertThat((String) method.invoke(service, Arrays.asList(spec(1L, 2L)))).isNotEqualTo(hash);
    }

    @Test
    void testCartesianProduct_2x2() throws Exception {
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
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
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
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
        SkuServiceImpl service = new SkuServiceImpl(null, null, null, null, null, null, null, null);
        Method method = SkuServiceImpl.class.getDeclaredMethod("cartesianProduct", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<List<String>> result = (List<List<String>>) method.invoke(service, new ArrayList<List<String>>());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEmpty();
    }
}
