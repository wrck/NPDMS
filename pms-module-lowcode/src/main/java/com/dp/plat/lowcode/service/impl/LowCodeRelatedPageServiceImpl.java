package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.exception.BusinessException;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeRelatedPage;
import com.dp.plat.lowcode.mapper.LowCodeRelatedPageMapper;
import com.dp.plat.lowcode.service.LowCodeRelatedPageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link LowCodeRelatedPageService} 实现。
 *
 * <p>状态机：DRAFT → PUBLISHED → ARCHIVED。配置 JSON 通过 Jackson 序列化导入导出。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeRelatedPageServiceImpl
        extends ServiceImpl<LowCodeRelatedPageMapper, LowCodeRelatedPage>
        implements LowCodeRelatedPageService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final ObjectMapper objectMapper;

    @Override
    public LowCodeRelatedPage getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<LowCodeRelatedPage>()
                .eq(LowCodeRelatedPage::getCode, code)
                .eq(LowCodeRelatedPage::getStatus, STATUS_PUBLISHED)
                .last("LIMIT 1"));
    }

    @Override
    public IPage<LowCodeRelatedPage> page(IPage<LowCodeRelatedPage> page, LowCodeConfigQuery query) {
        LambdaQueryWrapper<LowCodeRelatedPage> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getCode()), LowCodeRelatedPage::getCode, query.getCode())
                    .like(StringUtils.hasText(query.getName()), LowCodeRelatedPage::getName, query.getName())
                    .eq(StringUtils.hasText(query.getStatus()), LowCodeRelatedPage::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getBizType()), LowCodeRelatedPage::getBizType, query.getBizType());
        }
        wrapper.orderByDesc(LowCodeRelatedPage::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeRelatedPage create(LowCodeRelatedPage relatedPage) {
        if (relatedPage == null) {
            throw new BusinessException("关联页配置不能为空");
        }
        if (!StringUtils.hasText(relatedPage.getCode())) {
            throw new BusinessException("关联页编码不能为空");
        }
        if (!StringUtils.hasText(relatedPage.getName())) {
            throw new BusinessException("关联页名称不能为空");
        }
        long exists = this.count(new LambdaQueryWrapper<LowCodeRelatedPage>()
                .eq(LowCodeRelatedPage::getCode, relatedPage.getCode()));
        if (exists > 0) {
            throw new BusinessException("关联页编码已存在: " + relatedPage.getCode());
        }
        relatedPage.setId(null);
        if (!StringUtils.hasText(relatedPage.getStatus())) {
            relatedPage.setStatus(STATUS_DRAFT);
        }
        if (relatedPage.getVersion() == null) {
            relatedPage.setVersion(1);
        }
        this.save(relatedPage);
        return relatedPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeRelatedPage update(LowCodeRelatedPage relatedPage) {
        if (relatedPage == null || relatedPage.getId() == null) {
            throw new BusinessException("关联页配置或ID不能为空");
        }
        LowCodeRelatedPage existing = baseMapper.selectById(relatedPage.getId());
        if (existing == null) {
            throw new BusinessException("关联页配置不存在");
        }
        if (StringUtils.hasText(relatedPage.getCode()) && !relatedPage.getCode().equals(existing.getCode())) {
            long conflict = this.count(new LambdaQueryWrapper<LowCodeRelatedPage>()
                    .eq(LowCodeRelatedPage::getCode, relatedPage.getCode())
                    .ne(LowCodeRelatedPage::getId, relatedPage.getId()));
            if (conflict > 0) {
                throw new BusinessException("关联页编码已存在: " + relatedPage.getCode());
            }
        }
        relatedPage.setVersion(existing.getVersion());
        this.updateById(relatedPage);
        return baseMapper.selectById(relatedPage.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LowCodeRelatedPage existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("关联页配置不存在");
        }
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        LowCodeRelatedPage relatedPage = baseMapper.selectById(id);
        if (relatedPage == null) {
            throw new BusinessException("关联页配置不存在");
        }
        if (!STATUS_DRAFT.equals(relatedPage.getStatus())) {
            throw new BusinessException("当前状态不允许发布，当前状态: " + relatedPage.getStatus());
        }
        if (!StringUtils.hasText(relatedPage.getRelatedConfig())) {
            throw new BusinessException("关联页配置内容不能为空，无法发布");
        }
        relatedPage.setStatus(STATUS_PUBLISHED);
        this.updateById(relatedPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        LowCodeRelatedPage relatedPage = baseMapper.selectById(id);
        if (relatedPage == null) {
            throw new BusinessException("关联页配置不存在");
        }
        if (!STATUS_PUBLISHED.equals(relatedPage.getStatus())) {
            throw new BusinessException("当前状态不允许归档，当前状态: " + relatedPage.getStatus());
        }
        relatedPage.setStatus(STATUS_ARCHIVED);
        this.updateById(relatedPage);
    }

    @Override
    public byte[] exportConfig(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("关联页编码不能为空");
        }
        LowCodeRelatedPage relatedPage = this.getOne(new LambdaQueryWrapper<LowCodeRelatedPage>()
                .eq(LowCodeRelatedPage::getCode, code)
                .last("LIMIT 1"));
        if (relatedPage == null) {
            throw new BusinessException("关联页配置不存在: " + code);
        }
        try {
            String json = objectMapper.writeValueAsString(relatedPage);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("导出关联页配置失败, code={}", code, e);
            throw new BusinessException("导出关联页配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeRelatedPage importConfig(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BusinessException("导入的 JSON 内容不能为空");
        }
        LowCodeRelatedPage relatedPage;
        try {
            relatedPage = objectMapper.readValue(json, LowCodeRelatedPage.class);
        } catch (IOException e) {
            log.error("解析关联页配置 JSON 失败", e);
            throw new BusinessException("解析关联页配置 JSON 失败: " + e.getMessage());
        }
        if (!StringUtils.hasText(relatedPage.getCode())) {
            throw new BusinessException("导入的关联页配置缺少编码 code");
        }
        String originalCode = relatedPage.getCode();
        String newCode = originalCode;
        int suffix = 1;
        while (this.count(new LambdaQueryWrapper<LowCodeRelatedPage>().eq(LowCodeRelatedPage::getCode, newCode)) > 0) {
            newCode = originalCode + "_import" + suffix;
            suffix++;
        }
        relatedPage.setCode(newCode);
        relatedPage.setId(null);
        relatedPage.setVersion(1);
        relatedPage.setStatus(STATUS_DRAFT);
        this.save(relatedPage);
        return relatedPage;
    }
}
