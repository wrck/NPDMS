package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 割接背景答案。需求：CUT-03/10。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_result_cutover_background")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverResultCutoverBackgroundDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 所属统一结果版本；与根同事务写入 */
    private Long checklistResultId;
    /** 填写值：是否解决网上问题 */
    private Boolean solvesOnlineIssueFlag;
    /** 填写值：问题工单号 */
    private String issueTicketNo;
    /** 填写值：工单处理人 */
    private String issueHandler;
    /** 填写值：是否二次及以上割接 */
    private Boolean repeatCutoverFlag;
    /** 填写值：首次割接保障人 */
    private String firstCutoverOwner;
    /** 填写值：割接背景表述 */
    private String backgroundDescription;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
