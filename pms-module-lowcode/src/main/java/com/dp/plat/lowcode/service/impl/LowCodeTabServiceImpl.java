package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.exception.BusinessException;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeTab;
import com.dp.plat.lowcode.mapper.LowCodeTabMapper;
import com.dp.plat.lowcode.service.LowCodeTabService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link LowCodeTabService} 实现。
 *
 * <p>状态机：DRAFT → PUBLISHED → ARCHIVED。配置 JSON 通过 Jackson 序列化导入导出。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeTabServiceImpl
        extends ServiceImpl<LowCodeTabMapper, LowCodeTab>
        implements LowCodeTabService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final ObjectMapper objectMapper;

    @Override
    public LowCodeTab getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<LowCodeTab>()
                .eq(LowCodeTab::getCode, code)
                .eq(LowCodeTab::getStatus, STATUS_PUBLISHED)
                .last("LIMIT 1"));
    }

    @Override
    public IPage<LowCodeTab> page(IPage<LowCodeTab> page, LowCodeConfigQuery query) {
        LambdaQueryWrapper<LowCodeTab> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getCode()), LowCodeTab::getCode, query.getCode())
                    .like(StringUtils.hasText(query.getName()), LowCodeTab::getName, query.getName())
                    .eq(StringUtils.hasText(query.getStatus()), LowCodeTab::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getBizType()), LowCodeTab::getBizType, query.getBizType());
        }
        wrapper.orderByDesc(LowCodeTab::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeTab create(LowCodeTab tab) {
        if (tab == null) {
            throw new BusinessException("标签页配置不能为空");
        }
        if (!StringUtils.hasText(tab.getCode())) {
            throw new BusinessException("标签页编码不能为空");
        }
        if (!StringUtils.hasText(tab.getName())) {
            throw new BusinessException("标签页名称不能为空");
        }
        long exists = this.count(new LambdaQueryWrapper<LowCodeTab>()
                .eq(LowCodeTab::getCode, tab.getCode()));
        if (exists > 0) {
            throw new BusinessException("标签页编码已存在: " + tab.getCode());
        }
        tab.setId(null);
        if (!StringUtils.hasText(tab.getStatus())) {
            tab.setStatus(STATUS_DRAFT);
        }
        if (tab.getVersion() == null) {
            tab.setVersion(1);
        }
        this.save(tab);
        return tab;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeTab update(LowCodeTab tab) {
        if (tab == null || tab.getId() == null) {
            throw new BusinessException("标签页配置或ID不能为空");
        }
        LowCodeTab existing = baseMapper.selectById(tab.getId());
        if (existing == null) {
            throw new BusinessException("标签页配置不存在");
        }
        if (StringUtils.hasText(tab.getCode()) && !tab.getCode().equals(existing.getCode())) {
            long conflict = this.count(new LambdaQueryWrapper<LowCodeTab>()
                    .eq(LowCodeTab::getCode, tab.getCode())
                    .ne(LowCodeTab::getId, tab.getId()));
            if (conflict > 0) {
                throw new BusinessException("标签页编码已存在: " + tab.getCode());
            }
        }
        tab.setVersion(existing.getVersion());
        this.updateById(tab);
        return baseMapper.selectById(tab.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LowCodeTab existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("标签页配置不存在");
        }
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        LowCodeTab tab = baseMapper.selectById(id);
        if (tab == null) {
            throw new BusinessException("标签页配置不存在");
        }
        if (!STATUS_DRAFT.equals(tab.getStatus())) {
            throw new BusinessException("当前状态不允许发布，当前状态: " + tab.getStatus());
        }
        if (!StringUtils.hasText(tab.getTabConfig())) {
            throw new BusinessException("标签页配置内容不能为空，无法发布");
        }
        tab.setStatus(STATUS_PUBLISHED);
        this.updateById(tab);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        LowCodeTab tab = baseMapper.selectById(id);
        if (tab == null) {
            throw new BusinessException("标签页配置不存在");
        }
        if (!STATUS_PUBLISHED.equals(tab.getStatus())) {
            throw new BusinessException("当前状态不允许归档，当前状态: " + tab.getStatus());
        }
        tab.setStatus(STATUS_ARCHIVED);
        this.updateById(tab);
    }

    @Override
    public byte[] exportConfig(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("标签页编码不能为空");
        }
        LowCodeTab tab = this.getOne(new LambdaQueryWrapper<LowCodeTab>()
                .eq(LowCodeTab::getCode, code)
                .last("LIMIT 1"));
        if (tab == null) {
            throw new BusinessException("标签页配置不存在: " + code);
        }
        try {
            String json = objectMapper.writeValueAsString(tab);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("导出标签页配置失败, code={}", code, e);
            throw new BusinessException("导出标签页配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeTab importConfig(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BusinessException("导入的 JSON 内容不能为空");
        }
        LowCodeTab tab;
        try {
            tab = objectMapper.readValue(json, LowCodeTab.class);
        } catch (IOException e) {
            log.error("解析标签页配置 JSON 失败", e);
            throw new BusinessException("解析标签页配置 JSON 失败: " + e.getMessage());
        }
        if (!StringUtils.hasText(tab.getCode())) {
            throw new BusinessException("导入的标签页配置缺少编码 code");
        }
        String originalCode = tab.getCode();
        String newCode = originalCode;
        int suffix = 1;
        while (this.count(new LambdaQueryWrapper<LowCodeTab>().eq(LowCodeTab::getCode, newCode)) > 0) {
            newCode = originalCode + "_import" + suffix;
            suffix++;
        }
        tab.setCode(newCode);
        tab.setId(null);
        tab.setVersion(1);
        tab.setStatus(STATUS_DRAFT);
        this.save(tab);
        return tab;
    }
}
