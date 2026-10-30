package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;

import java.time.LocalDateTime;

/**
 * PMS 业务联调记录 DO（FR-ENG-024）。
 * <p>
 * 对应表 {@code imp_eng_joint_test}。
 * 状态：0 待联调、1 进行中、2 通过、3 失败。
 */
@TableName("imp_eng_joint_test")
@Data
@EqualsAndHashCode(callSuper = true)
public class JointTestDO extends BaseBusinessEntity {

    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 联调编码，项目内唯一
     */
    private String code;
    /**
     * 联调用例
     */
    private String testCase;
    /**
     * 关联设备编号
     */
    private Long equipmentId;
    /**
     * 参与方
     */
    private String participants;
    /**
     * 联调时间
     */
    private LocalDateTime testTime;
    /**
     * 联调人
     */
    private Long testerUserId;
    /**
     * 联调结果
     */
    private String result;
    /**
     * 异常记录
     */
    private String exceptionRecord;
    /**
     * 证据附件
     */
    private String evidenceUrl;
    /**
     * 状态：0 待联调 1 进行中 2 通过 3 失败
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
}
