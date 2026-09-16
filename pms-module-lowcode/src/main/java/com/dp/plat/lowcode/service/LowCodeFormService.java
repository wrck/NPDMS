package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeForm;

/**
 * 低代码表单配置 Service。
 *
 * <p>提供表单配置的 CRUD、按 code 查询已发布配置、状态流转（发布/归档）
 * 以及 JSON 导入导出能力。导出格式为表单配置的完整 JSON 序列化结果，
 * 导入时若 code 冲突则自动追加数字后缀以保证唯一性。</p>
 */
public interface LowCodeFormService extends IService<LowCodeForm> {

    /**
     * 按编码查询已发布（PUBLISHED）的表单配置。
     *
     * @param code 表单编码
     * @return 已发布的表单配置，不存在时返回 {@code null}
     */
    LowCodeForm getByCode(String code);

    /**
     * 分页查询表单配置。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<LowCodeForm> page(IPage<LowCodeForm> page, LowCodeConfigQuery query);

    /**
     * 创建表单配置（初始状态 DRAFT）。
     *
     * @param form 表单配置
     * @return 创建后的表单配置（含生成的 id）
     */
    LowCodeForm create(LowCodeForm form);

    /**
     * 更新表单配置。
     *
     * @param form 表单配置（须含 id）
     * @return 更新后的表单配置
     */
    LowCodeForm update(LowCodeForm form);

    /**
     * 按 id 逻辑删除表单配置。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 发布表单配置：DRAFT → PUBLISHED。校验 formConfig 不能为空。
     *
     * @param id 主键
     */
    void publish(Long id);

    /**
     * 归档表单配置：PUBLISHED → ARCHIVED。
     *
     * @param id 主键
     */
    void archive(Long id);

    /**
     * 导出指定编码的表单配置为 JSON 字节数组。
     *
     * @param code 表单编码
     * @return JSON 字节数组
     */
    byte[] exportConfig(String code);

    /**
     * 从 JSON 字符串导入表单配置。若 code 已存在则自动追加数字后缀。
     *
     * @param json JSON 字符串
     * @return 导入后的表单配置
     */
    LowCodeForm importConfig(String json);
}
