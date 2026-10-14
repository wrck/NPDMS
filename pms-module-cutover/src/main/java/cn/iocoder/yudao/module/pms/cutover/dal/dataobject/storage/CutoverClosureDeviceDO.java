package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 闭环冻结设备范围。需求：CUT-06。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_closure_device")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverClosureDeviceDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 闭环ID */
    private Long closureId;
    /** 设备ID */
    private Long deviceId;
    /** 当时所属项目 */
    private Long projectId;
    /** 当时序列号 */
    private String sn;
    /** 当时归属水位 */
    private Long projectAssignmentVersion;
    /** 原顺序 */
    private Integer sortOrder;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
