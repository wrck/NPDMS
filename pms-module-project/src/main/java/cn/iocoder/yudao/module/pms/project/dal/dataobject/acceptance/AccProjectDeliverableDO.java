package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** ACC Context拥有的项目交付件实例。 */
@TableName("acc_project_deliverable")
@Data
@EqualsAndHashCode(callSuper = true)
public class AccProjectDeliverableDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long projectId;
    private String deliverableCode;
    private String name;
    private String stageCode;
    private String taskCode;
    private Boolean required;
    private Long sourceDefinitionId;
    private String status;
    private Integer version;
}
