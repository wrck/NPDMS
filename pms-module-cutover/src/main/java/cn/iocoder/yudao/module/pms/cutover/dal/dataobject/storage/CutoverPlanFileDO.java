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

/** 方案固定槽位文件。需求：CUT-04/05；PLT文件。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_plan_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanFileDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 既有方案修订ID */
    private Long planRevisionId;
    /** 既有附件槽位 */
    private String fileRoleCode;
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
}
