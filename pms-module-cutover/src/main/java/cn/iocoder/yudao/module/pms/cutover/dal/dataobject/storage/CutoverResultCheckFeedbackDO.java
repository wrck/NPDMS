package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 风险及双机检查反馈。需求：CUT-03/09。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_result_check_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverResultCheckFeedbackDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 所属统一结果版本；与根同事务写入 */
    private Long checklistResultId;
    /** 填写值：是否正常/符合本次部署要求 */
    private Boolean checkNormalFlag;
    /** 填写值：是否执行；仅对应定义包含该字段时使用 */
    private Boolean executedFlag;
    /** 填写值：事实说明与特殊情况备注 */
    private String remark;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
