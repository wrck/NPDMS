package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.business;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Default business contract over the existing imp_eng_joint_test table. */
@TableName("imp_eng_joint_test")
@Data
@EqualsAndHashCode(callSuper = true)
@ProjectBusinessModel(ownerModule = "IMP", entityType = "jointTest", stableCode = "IMP_JOINT_TEST",
        name = "业务联调", permissionPrefix = "pms:imp-joint-test", nativeEntityType = "JOINT_TEST")
public class JointTestBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name = "联调编码", displayOrder = 10, writable = false)
    private String code;
    @BusinessModelField(name = "联调用例", displayOrder = 20)
    @jakarta.validation.constraints.NotBlank
    private String testCase;
    @BusinessModelField(name = "关联设备", displayOrder = 30)
    private Long equipmentId;
    @BusinessModelField(name = "参与方", displayOrder = 40)
    private String participants;
    @BusinessModelField(name = "联调时间", displayOrder = 50)
    private LocalDateTime testTime;
    @BusinessModelField(name = "联调人", displayOrder = 60)
    private Long testerUserId;
    @BusinessModelField(name = "联调结果", displayOrder = 70)
    private String result;
    @BusinessModelField(name = "异常记录", displayOrder = 80)
    private String exceptionRecord;
    @BusinessModelField(name = "证据附件", displayOrder = 90, writable = false)
    private String evidenceUrl;
    @BusinessModelField(name = "状态", displayOrder = 100, writable = false)
    private Integer status;
    @BusinessModelField(name = "备注", displayOrder = 110)
    private String remark;
}

