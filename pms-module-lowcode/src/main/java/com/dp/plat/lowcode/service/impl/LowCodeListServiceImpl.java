package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.exception.BusinessException;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeList;
import com.dp.plat.lowcode.mapper.LowCodeListMapper;
import com.dp.plat.lowcode.service.LowCodeListService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link LowCodeListService} 实现。
 *
 * <p>状态机：DRAFT → PUBLISHED → ARCHIVED。配置 JSON 通过 Jackson 序列化导入导出。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeListServiceImpl
        extends ServiceImpl<LowCodeListMapper, LowCodeList>
        implements LowCodeListService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final ObjectMapper objectMapper;

    @Override
    public LowCodeList getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<LowCodeList>()
                .eq(LowCodeList::getCode, code)
                .eq(LowCodeList::getStatus, STATUS_PUBLISHED)
                .last("LIMIT 1"));
    }

    @Override
    public IPage<LowCodeList> page(IPage<LowCodeList> page, LowCodeConfigQuery query) {
        LambdaQueryWrapper<LowCodeList> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getCode()), LowCodeList::getCode, query.getCode())
                    .like(StringUtils.hasText(query.getName()), LowCodeList::getName, query.getName())
                    .eq(StringUtils.hasText(query.getStatus()), LowCodeList::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getBizType()), LowCodeList::getBizType, query.getBizType());
        }
        wrapper.orderByDesc(LowCodeList::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeList create(LowCodeList list) {
        if (list == null) {
            throw new BusinessException("列表配置不能为空");
        }
        if (!StringUtils.hasText(list.getCode())) {
            throw new BusinessException("列表编码不能为空");
        }
        if (!StringUtils.hasText(list.getName())) {
            throw new BusinessException("列表名称不能为空");
        }
        long exists = this.count(new LambdaQueryWrapper<LowCodeList>()
                .eq(LowCodeList::getCode, list.getCode()));
        if (exists > 0) {
            throw new BusinessException("列表编码已存在: " + list.getCode());
        }
        list.setId(null);
        if (!StringUtils.hasText(list.getStatus())) {
            list.setStatus(STATUS_DRAFT);
        }
        if (list.getVersion() == null) {
            list.setVersion(1);
        }
        this.save(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeList update(LowCodeList list) {
        if (list == null || list.getId() == null) {
            throw new BusinessException("列表配置或ID不能为空");
        }
        LowCodeList existing = baseMapper.selectById(list.getId());
        if (existing == null) {
            throw new BusinessException("列表配置不存在");
        }
        if (StringUtils.hasText(list.getCode()) && !list.getCode().equals(existing.getCode())) {
            long conflict = this.count(new LambdaQueryWrapper<LowCodeList>()
                    .eq(LowCodeList::getCode, list.getCode())
                    .ne(LowCodeList::getId, list.getId()));
            if (conflict > 0) {
                throw new BusinessException("列表编码已存在: " + list.getCode());
            }
        }
        list.setVersion(existing.getVersion());
        this.updateById(list);
        return baseMapper.selectById(list.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LowCodeList existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("列表配置不存在");
        }
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        LowCodeList list = baseMapper.selectById(id);
        if (list == null) {
            throw new BusinessException("列表配置不存在");
        }
        if (!STATUS_DRAFT.equals(list.getStatus())) {
            throw new BusinessException("当前状态不允许发布，当前状态: " + list.getStatus());
        }
        if (!StringUtils.hasText(list.getListConfig())) {
            throw new BusinessException("列表配置内容不能为空，无法发布");
        }
        list.setStatus(STATUS_PUBLISHED);
        this.updateById(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        LowCodeList list = baseMapper.selectById(id);
        if (list == null) {
            throw new BusinessException("列表配置不存在");
        }
        if (!STATUS_PUBLISHED.equals(list.getStatus())) {
            throw new BusinessException("当前状态不允许归档，当前状态: " + list.getStatus());
        }
        list.setStatus(STATUS_ARCHIVED);
        this.updateById(list);
    }

    @Override
    public byte[] exportConfig(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("列表编码不能为空");
        }
        LowCodeList list = this.getOne(new LambdaQueryWrapper<LowCodeList>()
                .eq(LowCodeList::getCode, code)
                .last("LIMIT 1"));
        if (list == null) {
            throw new BusinessException("列表配置不存在: " + code);
        }
        try {
            String json = objectMapper.writeValueAsString(list);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("导出列表配置失败, code={}", code, e);
            throw new BusinessException("导出列表配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeList importConfig(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BusinessException("导入的 JSON 内容不能为空");
        }
        LowCodeList list;
        try {
            list = objectMapper.readValue(json, LowCodeList.class);
        } catch (IOException e) {
            log.error("解析列表配置 JSON 失败", e);
            throw new BusinessException("解析列表配置 JSON 失败: " + e.getMessage());
        }
        if (!StringUtils.hasText(list.getCode())) {
            throw new BusinessException("导入的列表配置缺少编码 code");
        }
        String originalCode = list.getCode();
        String newCode = originalCode;
        int suffix = 1;
        while (this.count(new LambdaQueryWrapper<LowCodeList>().eq(LowCodeList::getCode, newCode)) > 0) {
            newCode = originalCode + "_import" + suffix;
            suffix++;
        }
        list.setCode(newCode);
        list.setId(null);
        list.setVersion(1);
        list.setStatus(STATUS_DRAFT);
        this.save(list);
        return list;
    }
}
