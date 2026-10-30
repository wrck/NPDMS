package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** IMP来源水位集合。需求：EXE-06。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("imp_cutover_readiness_source_watermark")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverReadinessSourceWatermarkDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 所属快照来源事实 */
    private Long sourceFactId;
    /** 原水位轴 */
    private String axisCode;
    /** 原水位对象 */
    private Long objectId;
    /** 该轴/对象的原水位 */
    private Long factVersion;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
