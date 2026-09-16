package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeTab;

/**
 * 低代码标签页配置 Service。
 *
 * <p>提供标签页配置的 CRUD、按 code 查询已发布配置、状态流转（发布/归档）
 * 以及 JSON 导入导出能力。</p>
 */
public interface LowCodeTabService extends IService<LowCodeTab> {

    /**
     * 按编码查询已发布（PUBLISHED）的标签页配置。
     *
     * @param code 标签页编码
     * @return 已发布的标签页配置，不存在时返回 {@code null}
     */
    LowCodeTab getByCode(String code);

    /**
     * 分页查询标签页配置。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<LowCodeTab> page(IPage<LowCodeTab> page, LowCodeConfigQuery query);

    /**
     * 创建标签页配置（初始状态 DRAFT）。
     *
     * @param tab 标签页配置
     * @return 创建后的标签页配置（含生成的 id）
     */
    LowCodeTab create(LowCodeTab tab);

    /**
     * 更新标签页配置。
     *
     * @param tab 标签页配置（须含 id）
     * @return 更新后的标签页配置
     */
    LowCodeTab update(LowCodeTab tab);

    /**
     * 按 id 逻辑删除标签页配置。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 发布标签页配置：DRAFT → PUBLISHED。校验 tabConfig 不能为空。
     *
     * @param id 主键
     */
    void publish(Long id);

    /**
     * 归档标签页配置：PUBLISHED → ARCHIVED。
     *
     * @param id 主键
     */
    void archive(Long id);

    /**
     * 导出指定编码的标签页配置为 JSON 字节数组。
     *
     * @param code 标签页编码
     * @return JSON 字节数组
     */
    byte[] exportConfig(String code);

    /**
     * 从 JSON 字符串导入标签页配置。若 code 已存在则自动追加数字后缀。
     *
     * @param json JSON 字符串
     * @return 导入后的标签页配置
     */
    LowCodeTab importConfig(String json);
}
