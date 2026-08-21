package cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目门禁实例 DO（F-PM01 / V57 `proj_project_gate`）
 * <p>
 * 实例化时从模板冻结快照，初始状态 PENDING；`validation_summary` 由引用行生成摘要
 * （`TYPE:code;` 精简拼接，≤1000 字符）。
 * `source_definition_id` 为定义行映射槽（内容模型无定义行 ID 时为 NULL）。
 * `version` 列暂不接 @Version 拦截器：并发由 uk + 行锁保障。
 */
@TableName("proj_project_gate")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectGateInstanceDO extends TenantBaseDO {

    /**
     * 门禁实例ID
     */
    @TableId
    private Long id;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 门禁码（实例化时冻结，项目内唯一）
     */
    private String gateCode;
    /**
     * 门禁名称（快照）
     */
    private String name;
    /**
     * 类型：ENTRY准入/EXIT准出
     */
    private String gateType;
    /**
     * 所属阶段码
     */
    private String stageCode;
    /**
     * 门禁说明（快照）
     */
    private String description;
    /**
     * 冻结的校验内容摘要（实例化时自模板引用行生成，≤1000 字符）
     */
    private String validationSummary;
    /**
     * 冻结来源：模板门禁定义ID（内容模型无定义行 ID 时为 NULL）
     */
    private Long sourceDefinitionId;
    /**
     * 门禁状态（字典 pms_project_gate_status：PENDING/PASSED/FAILED，初始 PENDING）
     */
    private String status;
    /**
     * 乐观锁版本列：暂不接 @Version 拦截器，并发由 uk + 行锁保障
     */
    private Integer version;
}
