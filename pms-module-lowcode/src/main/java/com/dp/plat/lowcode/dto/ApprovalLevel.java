package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批级别 DTO（对应 LowCodeApprovalChain.levels JSON 数组中的一项）。
 *
 * <p>JSON 形如：{level:1, approverRole:"admin", name:"主管审批"}</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalLevel {

    /** 级别序号（从 1 开始） */
    private Integer level;

    /** 审批角色编码（对应 sys_role.role_code，如 admin / manager） */
    private String approverRole;

    /** 级别名称（如"主管审批"） */
    private String name;
}
