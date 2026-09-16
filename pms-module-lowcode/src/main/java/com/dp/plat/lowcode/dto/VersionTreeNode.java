package com.dp.plat.lowcode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本树节点（借鉴 Appsmith Git / OutSystems LifeTime 版本树可视化）。
 *
 * <p>按 parentVersionId 构建真正的分支树（借鉴 git parent commit 模型）：
 * 每个版本通过 parentVersionId 指向其父版本，从根版本（parentVersionId 为 null）开始递归构建多分支树。
 * 支持同 configType+configId 下的多个分支独立演进，前端 el-tree 可直接渲染分支拓扑。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionTreeNode {
    /** 版本记录主键 ID（用于 createBranch/addTag 定位具体版本） */
    private Long versionId;
    /** 父版本 ID（null=根版本，指向 base 版本构建分支树） */
    private Long parentVersionId;
    /** 分支名（默认 "main"） */
    private String branch;
    /** 标签（逗号分隔，标记里程碑如 v1.0-release/审核通过） */
    private String tags;
    /** 版本号 */
    private Integer version;
    /** 配置编码 */
    private String configCode;
    /** 变更说明 */
    private String changeLog;
    /** 状态: ACTIVE/ARCHIVED */
    private String status;
    /** 环境: DEV/TEST/PROD */
    private String environment;
    /** 操作人 */
    private String createBy;
    /** 创建时间 */
    private String createTime;
    /** 子版本节点（分支树时可有多个子节点：不同分支派生） */
    @Builder.Default
    private List<VersionTreeNode> children = new ArrayList<>();
}
