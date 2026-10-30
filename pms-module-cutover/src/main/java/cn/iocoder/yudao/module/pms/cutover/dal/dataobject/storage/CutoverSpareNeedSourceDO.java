package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 备件需求来源。需求：CUT-08；CUT-02/03。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_spare_need_source")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverSpareNeedSourceDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 外部申请引用 */
    private Long applicationReferenceId;
    /** ASSESSMENT或CHECKLIST_RISK */
    private String sourceTypeCode;
    /** 评估来源ID */
    private Long assessmentId;
    /** 评估来源版本 */
    private Long assessmentVersion;
    /** 当时适用的重大备件采集项 */
    private Long checklistItemId;
    /** 该清单项当时的并发版本；与结果修订无关 */
    private Long checklistItemVersion;
    /** 当时评估备件判断 */
    private Boolean sparePartAppliedFlag;
    /** 当时清单项适用事实 */
    private Boolean applicableFlag;
    /** 原顺序 */
    private Integer sortOrder;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
