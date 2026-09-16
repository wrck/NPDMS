package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低代码发布多级审批链。
 *
 * <p>借鉴 OutSystems LifeTime 多级审批：每个 configType 可配置一条启用审批链，
 * levels 为 JSON 数组：[{level:1, approverRole:"admin", name:"主管审批"}]。
 * 审批时按 level 顺序逐级推进，当前用户需具备对应角色方可通过当前级别。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_approval_chain")
public class LowCodeApprovalChain {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置类型: FORM/LIST/ENTITY/MICROFLOW/CONNECTOR/RULE/TAB/RELATED_PAGE */
    private String configType;

    /** 审批链名称 */
    private String name;

    /** 审批级别 JSON 字符串: [{level:1, approverRole:"admin", name:"主管审批"}] */
    private String levels;

    /** 是否启用：1=启用 / 0=停用 */
    private Integer enabled;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
