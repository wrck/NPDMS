package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeRelatedPage;

/**
 * 低代码关联页配置 Service。
 *
 * <p>提供关联页配置的 CRUD、按 code 查询已发布配置、状态流转（发布/归档）
 * 以及 JSON 导入导出能力。</p>
 */
public interface LowCodeRelatedPageService extends IService<LowCodeRelatedPage> {

    /**
     * 按编码查询已发布（PUBLISHED）的关联页配置。
     *
     * @param code 关联页编码
     * @return 已发布的关联页配置，不存在时返回 {@code null}
     */
    LowCodeRelatedPage getByCode(String code);

    /**
     * 分页查询关联页配置。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<LowCodeRelatedPage> page(IPage<LowCodeRelatedPage> page, LowCodeConfigQuery query);

    /**
     * 创建关联页配置（初始状态 DRAFT）。
     *
     * @param relatedPage 关联页配置
     * @return 创建后的关联页配置（含生成的 id）
     */
    LowCodeRelatedPage create(LowCodeRelatedPage relatedPage);

    /**
     * 更新关联页配置。
     *
     * @param relatedPage 关联页配置（须含 id）
     * @return 更新后的关联页配置
     */
    LowCodeRelatedPage update(LowCodeRelatedPage relatedPage);

    /**
     * 按 id 逻辑删除关联页配置。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 发布关联页配置：DRAFT → PUBLISHED。校验 relatedConfig 不能为空。
     *
     * @param id 主键
     */
    void publish(Long id);

    /**
     * 归档关联页配置：PUBLISHED → ARCHIVED。
     *
     * @param id 主键
     */
    void archive(Long id);

    /**
     * 导出指定编码的关联页配置为 JSON 字节数组。
     *
     * @param code 关联页编码
     * @return JSON 字节数组
     */
    byte[] exportConfig(String code);

    /**
     * 从 JSON 字符串导入关联页配置。若 code 已存在则自动追加数字后缀。
     *
     * @param json JSON 字符串
     * @return 导入后的关联页配置
     */
    LowCodeRelatedPage importConfig(String json);
}
