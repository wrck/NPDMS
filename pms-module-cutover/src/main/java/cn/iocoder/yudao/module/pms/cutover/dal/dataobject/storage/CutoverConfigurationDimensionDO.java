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

/** 配置维度。需求：CUT-07。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_configuration_dimension")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverConfigurationDimensionDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属配置修订 */
    private Long configurationRevisionId;
    /** 维度编码 */
    private String dimensionCode;
    /** 维度名称 */
    private String dimensionName;
    /** 维度值数据类型 */
    private String dataTypeCode;
    /** 权威字典/公开来源引用 */
    private String valueSource;
    /** 已确认的数据Owner */
    private String businessOwner;
    /** 受控任务上下文取值路径 */
    private String contextPath;
    /** 旧字典引用快照的原键；仅引用型历史不补造值 */
    private String dictionarySnapshotKey;
    /** 是否启用 */
    private Boolean enabledFlag;
    /** 顺序 */
    private Integer sortOrder;
}
