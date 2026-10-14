package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审批冻结结果集合。需求：CUT-05；CUT-03。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_approval_result_reference")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverApprovalResultReferenceDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 审批实例 */
    private Long approvalInstanceId;
    /** 采集项 */
    private Long checklistItemId;
    /** 本次冻结的统一结果主表ID */
    private Long checklistResultId;
    /** 本次冻结的结果版本号 */
    private Integer resultVersion;
    /** 本次提交时是否必选 */
    private Boolean requiredFlag;
    /** 展示顺序 */
    private Integer sortOrder;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
