package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 批准后保障联系信息变更。需求：CUT-04/05。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_plan_support_contact_change")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverPlanSupportContactChangeDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 原保障安排 */
    private Long supportArrangementId;
    /** 变更序号 */
    private Integer changeNo;
    /** 前一次联系信息变更 */
    private Long previousChangeId;
    /** 变更前人员 */
    private String beforePersonName;
    /** 变更前电话 */
    private String beforePhone;
    /** 变更前到位时间 */
    private LocalDateTime beforeArriveTime;
    /** 客户角色变更前CUS联系人引用 */
    private Long beforeCustomerContactId;
    /** 客户角色变更前CUS联系人版本 */
    private Long beforeCustomerContactVersion;
    /** 填写值：变更后人员 */
    private String afterPersonName;
    /** 填写值：变更后电话 */
    private String afterPhone;
    /** 填写值：变更后到位时间 */
    private LocalDateTime afterArriveTime;
    /** 客户角色变更后CUS联系人引用 */
    private Long afterCustomerContactId;
    /** 客户角色变更后CUS联系人版本 */
    private Long afterCustomerContactVersion;
    /** 填写值：变更原因 */
    private String reason;
    /** 实际操作者 */
    private Long operateUserId;
    /** 实际操作时间 */
    private LocalDateTime operateTime;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
