package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 评估冻结设备事实。需求：CUT-02；CUT-03。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_assessment_device")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverAssessmentDeviceDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 既有评估修订ID */
    private Long assessmentId;
    /** 对应原任务范围行 */
    private Long taskDeviceScopeId;
    /** 设备ID */
    private Long deviceId;
    /** 当时归属项目 */
    private Long projectId;
    /** 当时序列号 */
    private String sn;
    /** 当时归属水位 */
    private Long projectAssignmentVersion;
    /** 原设备类型编码 */
    private String deviceTypeCode;
    /** 原设备类型来源版本，不透明字符串 */
    private String deviceTypeSourceVersion;
    /** 原产品类型编码 */
    private String productTypeCode;
    /** 原产品类型来源版本，不透明字符串 */
    private String productTypeSourceVersion;
    /** 原顺序 */
    private Integer sortOrder;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
