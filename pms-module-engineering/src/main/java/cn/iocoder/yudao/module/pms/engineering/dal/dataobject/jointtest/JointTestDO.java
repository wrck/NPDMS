package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 业务联调记录 DO（FR-ENG-024）。
 * <p>
 * 对应表 {@code pms_eng_joint_test}。
 * 状态：0 待联调、1 进行中、2 通过、3 失败。
 */
@TableName("pms_eng_joint_test")
@Data
@EqualsAndHashCode(callSuper = true)
public class JointTestDO extends TenantBaseDO {

    @TableId
    private Long id;
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
    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
