package cn.iocoder.yudao.module.pms.asset.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AST设备割接文件事实。需求：CUT-06；AST/PLT。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("ast_device_cutover_result_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCutoverResultFileDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 本设备的AST割接结果根 */
    private Long deviceCutoverResultId;
    /** COLLECTION或MANUAL */
    private String fileSourceCode;
    /** 来源采集/证据阶段 */
    private String collectionStageCode;
    /** 原采集操作者 */
    private Long actorUserId;
    /** 原平台采集任务 */
    private String platformTaskId;
    /** 原平台结果版本 */
    private Long resultVersion;
    /** 原采集文件版本ID */
    private Long fileVersionId;
    /** 人工附件工件ID */
    private Long fileArtifactId;
    /** 人工附件固定版本 */
    private Integer fileVersionNo;
    /** 人工附件业务引用 */
    private String fileReferenceKey;
    /** 原附件摘要 */
    private String fileSha256;
    /** 原文件顺序 */
    private Integer sortOrder;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
