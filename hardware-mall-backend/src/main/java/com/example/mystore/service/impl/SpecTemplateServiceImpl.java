package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.SpecTemplate;
import com.example.mystore.mapper.SpecTemplateMapper;
import com.example.mystore.service.SpecTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecTemplateServiceImpl implements SpecTemplateService {

    private final SpecTemplateMapper specTemplateMapper;

    @Override
    public Page<SpecTemplate> getTemplatePage(Long categoryId, String name, Integer page, Integer limit) {
        Page<SpecTemplate> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<SpecTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecTemplate::getDeleteTime, 0);

        if (categoryId != null && categoryId > 0) {
            wrapper.eq(SpecTemplate::getCategoryId, categoryId);
        }

        if (StringUtils.hasText(name)) {
            wrapper.like(SpecTemplate::getName, name);
        }

        wrapper.orderByAsc(SpecTemplate::getSortOrder);
        return specTemplateMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public SpecTemplate getTemplateById(Long id) {
        SpecTemplate template = specTemplateMapper.selectById(id);
        if (template == null || template.getDeleteTime() != 0) {
            throw new RuntimeException("规格模板不存在");
        }
        return template;
    }

    @Override
    public List<SpecTemplate> getTemplatesByCategory(Long categoryId) {
        LambdaQueryWrapper<SpecTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecTemplate::getCategoryId, categoryId)
               .eq(SpecTemplate::getDeleteTime, 0)
               .orderByAsc(SpecTemplate::getSortOrder);
        return specTemplateMapper.selectList(wrapper);
    }

    @Override
    public SpecTemplate createTemplate(SpecTemplate template) {
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        template.setDeleteTime(0L);
        if (template.getSpecType() == null) {
            template.setSpecType(1);
        }
        if (template.getIsRequired() == null) {
            template.setIsRequired(1);
        }
        if (template.getSortOrder() == null) {
            template.setSortOrder(0);
        }
        specTemplateMapper.insert(template);
        return template;
    }

    @Override
    public SpecTemplate updateTemplate(SpecTemplate template) {
        SpecTemplate exist = specTemplateMapper.selectById(template.getId());
        if (exist == null) {
            throw new RuntimeException("规格模板不存在");
        }

        if (StringUtils.hasText(template.getName())) {
            exist.setName(template.getName());
        }
        if (template.getSpecType() != null) {
            exist.setSpecType(template.getSpecType());
        }
        if (template.getIsRequired() != null) {
            exist.setIsRequired(template.getIsRequired());
        }
        if (template.getSortOrder() != null) {
            exist.setSortOrder(template.getSortOrder());
        }
        if (template.getCategoryId() != null) {
            exist.setCategoryId(template.getCategoryId());
        }
        exist.setUpdateTime(LocalDateTime.now());
        specTemplateMapper.updateById(exist);
        return exist;
    }

    @Override
    public void deleteTemplate(Long id) {
        SpecTemplate template = specTemplateMapper.selectById(id);
        if (template == null) {
            throw new RuntimeException("规格模板不存在");
        }
        template.setDeleteTime(System.currentTimeMillis());
        specTemplateMapper.updateById(template);
    }
}
