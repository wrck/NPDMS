package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.LowCodeConfigQuery;
import com.dp.plat.lowcode.entity.LowCodeList;

/**
 * 低代码列表配置 Service。
 *
 * <p>提供列表配置的 CRUD、按 code 查询已发布配置、状态流转（发布/归档）
 * 以及 JSON 导入导出能力。</p>
 */
public interface LowCodeListService extends IService<LowCodeList> {

    /**
     * 按编码查询已发布（PUBLISHED）的列表配置。
     *
     * @param code 列表编码
     * @return 已发布的列表配置，不存在时返回 {@code null}
     */
    LowCodeList getByCode(String code);

    /**
     * 分页查询列表配置。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<LowCodeList> page(IPage<LowCodeList> page, LowCodeConfigQuery query);

    /**
     * 创建列表配置（初始状态 DRAFT）。
     *
     * @param list 列表配置
     * @return 创建后的列表配置（含生成的 id）
     */
    LowCodeList create(LowCodeList list);

    /**
     * 更新列表配置。
     *
     * @param list 列表配置（须含 id）
     * @return 更新后的列表配置
     */
    LowCodeList update(LowCodeList list);

    /**
     * 按 id 逻辑删除列表配置。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 发布列表配置：DRAFT → PUBLISHED。校验 listConfig 不能为空。
     *
     * @param id 主键
     */
    void publish(Long id);

    /**
     * 归档列表配置：PUBLISHED → ARCHIVED。
     *
     * @param id 主键
     */
    void archive(Long id);

    /**
     * 导出指定编码的列表配置为 JSON 字节数组。
     *
     * @param code 列表编码
     * @return JSON 字节数组
     */
    byte[] exportConfig(String code);

    /**
     * 从 JSON 字符串导入列表配置。若 code 已存在则自动追加数字后缀。
     *
     * @param json JSON 字符串
     * @return 导入后的列表配置
     */
    LowCodeList importConfig(String json);
}
