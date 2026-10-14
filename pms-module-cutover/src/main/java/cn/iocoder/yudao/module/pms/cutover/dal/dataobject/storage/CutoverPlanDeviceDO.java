package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 方案设备资料行。需求：CUT-04。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_plan_device")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanDeviceDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 既有方案修订ID */
    private Long planRevisionId;
    /** 评估冻结设备行 */
    private Long assessmentDeviceId;
    /** 设备ID */
    private Long deviceId;
    /** 来源序列号 */
    private String sn;
    /** 来源归属水位 */
    private Long projectAssignmentVersion;
    /** 来源设备类型 */
    private String deviceTypeCode;
    /** 来源设备类型版本 */
    private String deviceTypeSourceVersion;
    /** 填写值：方案设备型号 */
    private String productModel;
    /** 填写值：方案当前版本 */
    private String currentSoftwareVersion;
    /** 填写值：方案目标版本 */
    private String targetSoftwareVersion;
    /** 填写值：备注 */
    private String remark;
    /** 顺序 */
    private Integer sortOrder;
}
