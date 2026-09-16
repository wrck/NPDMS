package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.entity.LowCodeConfigTemplate;
import com.dp.plat.lowcode.mapper.LowCodeConfigTemplateMapper;
import com.dp.plat.lowcode.service.ConfigTemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 低代码配置模板服务实现（批次5-T8 模板市场）。
 *
 * <p>核心能力：
 * <ul>
 *   <li>save：按 code 去重（存在则 updateById，不存在则 insert），并填充默认值；</li>
 *   <li>marketplace：仅 PUBLISHED，关键词搜索 name/tags/description，按 downloadCount desc；</li>
 *   <li>download：downloadCount++ 后对 configJson 做 {{key}} 占位符替换（参数化），required 缺失抛异常；</li>
 *   <li>rate：增量平均评分 (rating*ratingCount + newRating)/(ratingCount+1)，保留 1 位小数。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigTemplateServiceImpl
        extends ServiceImpl<LowCodeConfigTemplateMapper, LowCodeConfigTemplate>
        implements ConfigTemplateService {

    private final ObjectMapper objectMapper;

    @Override
    public boolean save(LowCodeConfigTemplate template) {
        // 按 code 去重：存在则 updateById，不存在则 insert
        LowCodeConfigTemplate existing = getOne(new LambdaQueryWrapper<LowCodeConfigTemplate>()
                .eq(LowCodeConfigTemplate::getCode, template.getCode()));
        if (existing != null) {
            template.setId(existing.getId());
            return updateById(template);
        }
        // 新建时填充默认值
        if (template.getStatus() == null) {
            template.setStatus("DRAFT");
        }
        if (template.getDownloadCount() == null) {
            template.setDownloadCount(0);
        }
        if (template.getRating() == null) {
            template.setRating(BigDecimal.ZERO);
        }
        if (template.getRatingCount() == null) {
            template.setRatingCount(0);
        }
        if (template.getVersion() == null) {
            template.setVersion("1.0.0");
        }
        return super.save(template);
    }

    @Override
    public LowCodeConfigTemplate publish(Long id) {
        LowCodeConfigTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + id);
        }
        template.setStatus("PUBLISHED");
        updateById(template);
        return template;
    }

    @Override
    public LowCodeConfigTemplate unpublish(Long id) {
        LowCodeConfigTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + id);
        }
        template.setStatus("DRAFT");
        updateById(template);
        return template;
    }

    @Override
    public LowCodeConfigTemplate archive(Long id) {
        LowCodeConfigTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + id);
        }
        template.setStatus("ARCHIVED");
        updateById(template);
        return template;
    }

    @Override
    public LowCodeConfigTemplate getByCode(String code) {
        return getOne(new LambdaQueryWrapper<LowCodeConfigTemplate>()
                .eq(LowCodeConfigTemplate::getCode, code));
    }

    @Override
    public List<LowCodeConfigTemplate> listAll() {
        return list();
    }

    @Override
    public List<LowCodeConfigTemplate> marketplace(String keyword, String configType, String category) {
        LambdaQueryWrapper<LowCodeConfigTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LowCodeConfigTemplate::getStatus, "PUBLISHED");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(LowCodeConfigTemplate::getName, keyword)
                    .or().like(LowCodeConfigTemplate::getTags, keyword)
                    .or().like(LowCodeConfigTemplate::getDescription, keyword));
        }
        if (configType != null && !configType.isBlank()) {
            wrapper.eq(LowCodeConfigTemplate::getConfigType, configType);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(LowCodeConfigTemplate::getCategory, category);
        }
        wrapper.orderByDesc(LowCodeConfigTemplate::getDownloadCount);
        return list(wrapper);
    }

    @Override
    public LowCodeConfigTemplate download(Long id, Map<String, Object> parameters) {
        LowCodeConfigTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + id);
        }
        // 增加下载计数
        template.setDownloadCount((template.getDownloadCount() == null ? 0 : template.getDownloadCount()) + 1);
        updateById(template);

        // 参数化替换：解析 parameters 定义 JSON，对 configJson 做 {{key}} → value 替换
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        String paramDefJson = template.getParameters();
        if (paramDefJson != null && !paramDefJson.isBlank() && template.getConfigJson() != null) {
            try {
                List<Map<String, Object>> paramDefs = objectMapper.readValue(
                        paramDefJson, new TypeReference<List<Map<String, Object>>>() {});
                String configJson = template.getConfigJson();
                for (Map<String, Object> def : paramDefs) {
                    String key = String.valueOf(def.get("key"));
                    boolean required = Boolean.TRUE.equals(def.get("required"));
                    Object value = params.get(key);
                    if (required && (value == null || (value instanceof String s && s.isBlank()))) {
                        throw new RuntimeException("缺少必填参数: " + key);
                    }
                    if (value != null) {
                        configJson = configJson.replace("{{" + key + "}}", String.valueOf(value));
                    }
                }
                template.setConfigJson(configJson);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("模板参数化替换失败: id={}", id, e);
                throw new RuntimeException("模板参数化替换失败: " + e.getMessage(), e);
            }
        }
        return template;
    }

    @Override
    public LowCodeConfigTemplate rate(Long id, BigDecimal newRating) {
        LowCodeConfigTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("模板不存在: " + id);
        }
        BigDecimal currentRating = template.getRating() == null ? BigDecimal.ZERO : template.getRating();
        int currentCount = template.getRatingCount() == null ? 0 : template.getRatingCount();
        // 增量平均评分 = (rating * ratingCount + newRating) / (ratingCount + 1)
        BigDecimal updated = currentRating.multiply(BigDecimal.valueOf(currentCount))
                .add(newRating)
                .divide(BigDecimal.valueOf(currentCount + 1), 1, RoundingMode.HALF_UP);
        template.setRating(updated);
        template.setRatingCount(currentCount + 1);
        updateById(template);
        return template;
    }

    @Override
    public List<LowCodeConfigTemplate> listVersions(String code) {
        return list(new LambdaQueryWrapper<LowCodeConfigTemplate>()
                .eq(LowCodeConfigTemplate::getCode, code)
                .orderByDesc(LowCodeConfigTemplate::getVersion));
    }
}
