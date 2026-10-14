package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统一结果文件引用。需求：CUT-03/09/10；PLT文件。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_result_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverChecklistResultFileDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 统一结果ID */
    private Long checklistResultId;
    /** 真正自定义表格中的所属行；固定表格目前无文件列，不加多态表名/行ID */
    private Long customResultRowId;
    /** 所属文件字段定义；整个FILE项时引用根定义 */
    private Long fieldDefinitionId;
    /** 正常结果文件或人工降级证据的既有角色 */
    private String fileRoleCode;
    /** 本结果内文件序号 */
    private Integer fileNo;
    /** PLT工件ID；文件本体仍由PLT保存 */
    private Long fileArtifactId;
    /** 固定文件版本号 */
    private Integer fileVersionNo;
    /** PLT业务引用键 */
    private String fileReferenceKey;
    /** 文件工件事实水位 */
    private Long fileArtifactFactVersion;
    /** 文件引用事实水位 */
    private Long fileReferenceFactVersion;
    /** 文件可用性事实水位 */
    private Long fileAvailabilityFactVersion;
    /** 登记时范围版本；不据此否定当前合法下载 */
    private Long fileScopeVersion;
    /** 已有文件内容摘要 */
    private String fileSha256;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
