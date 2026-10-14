package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 外部加载记录。需求：CUT-03/07/09。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_external_query")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverChecklistExternalQueryDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 发起加载的清单项 */
    private Long checklistItemId;
    /** 使用的定义修订 */
    private Long itemDefinitionRevisionId;
    /** 查询对应设备 */
    private Long deviceId;
    /** 使用的正式接口标识 */
    private String interfaceCode;
    /** 项内查询序号 */
    private Integer queryNo;
    /** CURRENT或TARGET等正式受控查询语境 */
    private String versionContextCode;
    /** 本次实际查询软件版本 */
    private String queriedSoftwareVersion;
    /** 本次实际设备型号 */
    private String productModel;
    /** 本次调用的幂等/跟踪身份 */
    private String requestId;
    /** 发起查询人 */
    private Long queryUserId;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 完成时间 */
    private LocalDateTime finishTime;
    /** NULL未完成、true成功、false失败；不是CUT业务状态 */
    private Boolean succeededFlag;
    /** 实际失败编码 */
    private String failureCode;
    /** 脱敏失败说明 */
    private String failureDetail;
    /** 成功返回的实际行数；0不能与失败混用 */
    private Integer returnedRowCount;
    /** 首次装载的统一结果ID */
    private Long loadedChecklistResultId;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
