package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 低代码配置版本快照。
 *
 * <p>每次发布操作生成不可变的全量 JSON 快照，支持版本历史查看、Diff 对比与回滚。
 * 按 environment（DEV/TEST/PROD）区分环境，支持环境间配置包晋升。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_config_version")
public class LowCodeConfigVersion extends BaseEntity {

    /** 配置类型: FORM/LIST/TAB/RELATED_PAGE/ENTITY/MICROFLOW/RULE/CONNECTOR */
    @NotBlank(message = "配置类型不能为空")
    @Size(max = 32, message = "配置类型长度不能超过 32 个字符")
    private String configType;

    @NotNull(message = "配置ID不能为空")
    private Long configId;

    @NotBlank(message = "配置编码不能为空")
    @Size(max = 64, message = "配置编码长度不能超过 64 个字符")
    private String configCode;

    @NotNull(message = "版本号不能为空")
    private Integer version;

    @NotBlank(message = "快照不能为空")
    private String snapshot;

    @Size(max = 512, message = "变更说明长度不能超过 512 个字符")
    private String changeLog;

    /** 状态: ACTIVE/ARCHIVED */
    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "ACTIVE";

    /** 环境: DEV/TEST/PROD */
    @NotBlank(message = "环境不能为空")
    @Size(max = 16, message = "环境长度不能超过 16 个字符")
    @Builder.Default
    private String environment = "DEV";

    /**
     * 父版本 ID（批次5-T1：版本树分支支持）。
     *
     * <p>指向当前版本的父版本（base 版本），用于构建真正的分支树而非线性链。
     * 为 null 表示根版本（首次创建的版本）。
     * 借鉴 git 的 parent commit 模型：从 v2 分支出 v3a 与 v3b 时，两者 parentId 都指向 v2。</p>
     */
    private Long parentVersionId;

    /**
     * 分支名（批次5-T1：版本树分支支持）。
     *
     * <p>标识版本所属分支，默认 "main"。从某版本创建分支时，新版本 branch 为指定分支名。
     * 同一 configType+configId 下可有多个分支，每个分支独立演进。
     * 借鉴 git branch 模型，但简化为扁平分支（不支持 merge）。</p>
     */
    @Size(max = 64, message = "分支名长度不能超过 64 个字符")
    @Builder.Default
    private String branch = "main";

    /**
     * 标签（批次5-T1：版本树分支支持）。
     *
     * <p>可选的版本标签，用于标记里程碑（如 "v1.0-release"、"审核通过"、"生产发布"）。
     * 与 git tag 不同，此处为简单字符串标签，不支持标签注释。
     * 一个版本可有多个标签（逗号分隔），也可无标签。</p>
     */
    @Size(max = 128, message = "标签长度不能超过 128 个字符")
    private String tags;
}
