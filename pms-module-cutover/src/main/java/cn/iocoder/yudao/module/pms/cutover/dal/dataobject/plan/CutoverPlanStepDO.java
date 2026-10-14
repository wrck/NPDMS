package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_step")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanStepDO extends TenantBaseDO {
    @TableId private Long id;
    private Long planRevisionId;
    private String sectionCode;
    private Integer stepNo;
    private String content;
    @Version private Integer version;


    /** 填写值：步骤名称 */
    private String stepName;
    /** 填写值：测试结果 */
    private String testResult;
    /** 填写值：业务影响时间说明 */
    private String businessImpactTime;
    /** 填写值：操作人员说明 */
    private String operatorName;
    /** 填写值：操作时间说明，保持自由文本 */
    private String operationTime;
}
