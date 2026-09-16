package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 规则测试用例实体（批次3-T3）。
 *
 * <p>为规则定义可重复执行的测试用例，包含输入事实和期望输出。
 * 测试运行时对比实际输出与期望输出，判定 PASS/FAIL。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_rule_test_case")
public class LowCodeRuleTestCase extends BaseEntity {

    /** 规则 ID */
    private Long ruleId;

    /** 规则编码（冗余，便于查询） */
    private String ruleCode;

    /** 测试用例名称 */
    private String name;

    /** 测试用例描述 */
    private String description;

    /** 输入事实 JSON（决策表为 facts，表达式为 context） */
    private String inputJson;

    /** 期望输出 JSON（决策表为 actions 列表，表达式为 result） */
    private String expectedOutputJson;

    /** 断言模式：EQUALS（完全相等）/ CONTAINS（实际包含期望）/ NOT_NULL（非空即可） */
    private String assertionMode = "EQUALS";

    /** 是否启用 */
    private Boolean enabled = true;
}
