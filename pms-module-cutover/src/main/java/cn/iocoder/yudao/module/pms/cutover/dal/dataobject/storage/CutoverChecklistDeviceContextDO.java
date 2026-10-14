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

/** P3设备及软件版本上下文。需求：CUT-03/09。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_device_context")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistDeviceContextDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 清单修订ID */
    private Long checklistId;
    /** 评估冻结设备行 */
    private Long assessmentDeviceId;
    /** 设备ID */
    private Long deviceId;
    /** 该设备实际型号上下文 */
    private String productModel;
    /** 填写/选择的当前软件版本 */
    private String currentSoftwareVersion;
    /** 版本升级时填写/选择的目标版本 */
    private String targetSoftwareVersion;
    /** 显示顺序 */
    private Integer sortOrder;
}
