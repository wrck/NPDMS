package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.exception.BusinessException;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeForm;
import com.dp.plat.lowcode.mapper.LowCodeFormMapper;
import com.dp.plat.lowcode.service.LowCodeFormService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link LowCodeFormService} 实现。
 *
 * <p>使用 Jackson {@link ObjectMapper} 进行配置 JSON 的序列化与反序列化。
 * 状态机：DRAFT → PUBLISHED → ARCHIVED，非法状态流转抛出 {@link BusinessException}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeFormServiceImpl
        extends ServiceImpl<LowCodeFormMapper, LowCodeForm>
        implements LowCodeFormService {

    /** 草稿状态 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 已归档状态 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final ObjectMapper objectMapper;

    @Override
    public LowCodeForm getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return baseMapper.selectOne(new LambdaQueryWrapper<LowCodeForm>()
                .eq(LowCodeForm::getCode, code)
                .eq(LowCodeForm::getStatus, STATUS_PUBLISHED)
                .last("LIMIT 1"));
    }

    @Override
    public IPage<LowCodeForm> page(IPage<LowCodeForm> page, LowCodeConfigQuery query) {
        LambdaQueryWrapper<LowCodeForm> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getCode()), LowCodeForm::getCode, query.getCode())
                    .like(StringUtils.hasText(query.getName()), LowCodeForm::getName, query.getName())
                    .eq(StringUtils.hasText(query.getStatus()), LowCodeForm::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getBizType()), LowCodeForm::getBizType, query.getBizType());
        }
        wrapper.orderByDesc(LowCodeForm::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeForm create(LowCodeForm form) {
        if (form == null) {
            throw new BusinessException("表单配置不能为空");
        }
        if (!StringUtils.hasText(form.getCode())) {
            throw new BusinessException("表单编码不能为空");
        }
        if (!StringUtils.hasText(form.getName())) {
            throw new BusinessException("表单名称不能为空");
        }
        // 校验 code 唯一性
        long exists = this.count(new LambdaQueryWrapper<LowCodeForm>()
                .eq(LowCodeForm::getCode, form.getCode()));
        if (exists > 0) {
            throw new BusinessException("表单编码已存在: " + form.getCode());
        }
        form.setId(null);
        if (!StringUtils.hasText(form.getStatus())) {
            form.setStatus(STATUS_DRAFT);
        }
        if (form.getVersion() == null) {
            form.setVersion(1);
        }
        this.save(form);
        return form;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeForm update(LowCodeForm form) {
        if (form == null || form.getId() == null) {
            throw new BusinessException("表单配置或ID不能为空");
        }
        LowCodeForm existing = baseMapper.selectById(form.getId());
        if (existing == null) {
            throw new BusinessException("表单配置不存在");
        }
        // 若修改了 code，需校验新 code 不与其他记录冲突
        if (StringUtils.hasText(form.getCode()) && !form.getCode().equals(existing.getCode())) {
            long conflict = this.count(new LambdaQueryWrapper<LowCodeForm>()
                    .eq(LowCodeForm::getCode, form.getCode())
                    .ne(LowCodeForm::getId, form.getId()));
            if (conflict > 0) {
                throw new BusinessException("表单编码已存在: " + form.getCode());
            }
        }
        // 保持乐观锁 version 与库中一致，由 MyBatis-Plus 拦截器处理自增
        form.setVersion(existing.getVersion());
        this.updateById(form);
        return baseMapper.selectById(form.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LowCodeForm existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("表单配置不存在");
        }
        baseMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        LowCodeForm form = baseMapper.selectById(id);
        if (form == null) {
            throw new BusinessException("表单配置不存在");
        }
        if (!STATUS_DRAFT.equals(form.getStatus())) {
            throw new BusinessException("当前状态不允许发布，当前状态: " + form.getStatus());
        }
        if (!StringUtils.hasText(form.getFormConfig())) {
            throw new BusinessException("表单配置内容不能为空，无法发布");
        }
        form.setStatus(STATUS_PUBLISHED);
        this.updateById(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        LowCodeForm form = baseMapper.selectById(id);
        if (form == null) {
            throw new BusinessException("表单配置不存在");
        }
        if (!STATUS_PUBLISHED.equals(form.getStatus())) {
            throw new BusinessException("当前状态不允许归档，当前状态: " + form.getStatus());
        }
        form.setStatus(STATUS_ARCHIVED);
        this.updateById(form);
    }

    @Override
    public byte[] exportConfig(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("表单编码不能为空");
        }
        LowCodeForm form = baseMapper.selectOne(new LambdaQueryWrapper<LowCodeForm>()
                .eq(LowCodeForm::getCode, code)
                .last("LIMIT 1"));
        if (form == null) {
            throw new BusinessException("表单配置不存在: " + code);
        }
        try {
            String json = objectMapper.writeValueAsString(form);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("导出表单配置失败, code={}", code, e);
            throw new BusinessException("导出表单配置失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeForm importConfig(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BusinessException("导入的 JSON 内容不能为空");
        }
        LowCodeForm form;
        try {
            form = objectMapper.readValue(json, LowCodeForm.class);
        } catch (IOException e) {
            log.error("解析表单配置 JSON 失败", e);
            throw new BusinessException("解析表单配置 JSON 失败: " + e.getMessage());
        }
        if (!StringUtils.hasText(form.getCode())) {
            throw new BusinessException("导入的表单配置缺少编码 code");
        }
        // code 冲突时自动追加数字后缀
        String originalCode = form.getCode();
        String newCode = originalCode;
        int suffix = 1;
        while (this.count(new LambdaQueryWrapper<LowCodeForm>().eq(LowCodeForm::getCode, newCode)) > 0) {
            newCode = originalCode + "_import" + suffix;
            suffix++;
        }
        form.setCode(newCode);
        form.setId(null);
        form.setVersion(1);
        form.setStatus(STATUS_DRAFT);
        this.save(form);
        return form;
    }
}
