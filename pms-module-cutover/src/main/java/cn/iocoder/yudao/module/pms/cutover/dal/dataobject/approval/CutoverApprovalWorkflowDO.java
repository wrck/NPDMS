package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** ID为CUT审批实例ID；一经建立不改写BPM运行身份。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cut_approval_workflow")
public class CutoverApprovalWorkflowDO extends BaseBusinessEntity {
    private Long taskId;
    private String processInstanceId;
}
