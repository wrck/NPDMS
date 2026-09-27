package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** ACC Context拥有的项目交付件实例。 */
@TableName("acc_project_deliverable")
@Data
@EqualsAndHashCode(callSuper = true)
public class AccProjectDeliverableDO extends BaseBusinessEntity {
    private Long projectId;
    private String deliverableCode;
    private String name;
    private String stageCode;
    private String taskCode;
    private Boolean required;
    private Long sourceDefinitionId;
    private String status;
    private Long currentSourceVersionId;
    private String archiveStatus;
}
