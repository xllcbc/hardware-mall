package com.example.mystore.service.impl;

import com.example.mystore.entity.db.SpecItem;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.mapper.SpecItemMapper;
import com.example.mystore.mapper.SpecTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecItemServiceImplTest {

    @Mock
    private SpecItemMapper specItemMapper;

    @Mock
    private SpecTemplateMapper specTemplateMapper;

    @InjectMocks
    private SpecItemServiceImpl specItemService;

    private SpecTemplate template(Long id) {
        SpecTemplate t = new SpecTemplate();
        t.setId(id);
        t.setCategoryId(1L);
        t.setDeleteTime(0L);
        return t;
    }

    @Test
    void getItemsByCategory_noTemplates_returnsEmptyWithoutItemQuery() {
        // M5: 分类下没有模板时不该查规格项表, 更不该返回全库规格项
        when(specTemplateMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<SpecItem> result = specItemService.getItemsByCategory(1L);

        assertThat(result).isEmpty();
        verify(specItemMapper, never()).selectList(any());
    }

    @Test
    void getItemsByCategory_filtersByTemplateIds() {
        when(specTemplateMapper.selectList(any())).thenReturn(List.of(template(10L), template(20L)));
        SpecItem item = new SpecItem();
        item.setId(100L);
        item.setTemplateId(10L);
        when(specItemMapper.selectList(any())).thenReturn(List.of(item));

        List<SpecItem> result = specItemService.getItemsByCategory(1L);

        assertThat(result).containsExactly(item);
        verify(specTemplateMapper).selectList(any());
        verify(specItemMapper).selectList(any());
    }
}
