package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeConfigTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 低代码配置模板服务（批次5-T8 模板市场）。
 *
 * <p>提供模板的上架/下架/归档、市场浏览与搜索、下载（含参数化替换）、
 * 评分聚合以及版本查询能力。</p>
 *
 * <p>注意：{@code save} 继承自 {@link IService}（返回 boolean），实现类覆盖该方法
 * 以加入 code 去重逻辑（存在则 updateById，不存在则 insert），与
 * {@code LowCodeConnectorServiceImpl} 保持一致。Controller 通过 {@code Result.ok(template)}
 * 返回保存后的实体。</p>
 */
public interface ConfigTemplateService extends IService<LowCodeConfigTemplate> {

    /**
     * 保存或更新模板（按 code 去重，重复则更新）。
     *
     * <p>覆盖 {@link IService#save} 以加入 code 去重逻辑。</p>
     *
     * @param template 模板
     * @return 是否成功
     */
    @Override
    boolean save(LowCodeConfigTemplate template);

    /**
     * 上架模板（status → PUBLISHED）。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     */
    LowCodeConfigTemplate publish(Long id);

    /**
     * 下架模板（status → DRAFT）。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     */
    LowCodeConfigTemplate unpublish(Long id);

    /**
     * 归档模板（status → ARCHIVED）。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     */
    LowCodeConfigTemplate archive(Long id);

    /**
     * 按 code 查询模板。
     *
     * @param code 模板编码
     * @return 模板
     */
    LowCodeConfigTemplate getByCode(String code);

    /**
     * 列出所有模板（含 DRAFT/ARCHIVED，管理用）。
     *
     * @return 模板列表
     */
    List<LowCodeConfigTemplate> listAll();

    /**
     * 市场浏览（仅 PUBLISHED），支持关键词搜索 name/tags/description，
     * configType/category 过滤，按 downloadCount desc 排序。
     *
     * @param keyword    关键词
     * @param configType 配置类型
     * @param category   分类
     * @return 模板列表
     */
    List<LowCodeConfigTemplate> marketplace(String keyword, String configType, String category);

    /**
     * 下载模板（downloadCount++），应用参数化替换后返回 configJson。
     *
     * <p>参数校验：required 参数缺失抛异常。</p>
     *
     * @param id         模板 ID
     * @param parameters 参数键值对
     * @return 替换后的模板（configJson 已替换占位符）
     */
    LowCodeConfigTemplate download(Long id, Map<String, Object> parameters);

    /**
     * 评分（更新 rating 平均值与 ratingCount）。
     *
     * @param id     模板 ID
     * @param rating 新评分（0-5）
     * @return 更新后的模板
     */
    LowCodeConfigTemplate rate(Long id, BigDecimal rating);

    /**
     * 查询某 code 的所有版本（按 version desc 排序）。
     *
     * @param code 模板编码
     * @return 模板列表
     */
    List<LowCodeConfigTemplate> listVersions(String code);
}
