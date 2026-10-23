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

/** 配置冻结字典值。需求：CUT-07。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_configuration_dictionary_value")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverConfigurationDictionaryValueDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属配置修订 */
    private Long configurationRevisionId;
    /** 字典类型 */
    private String dictType;
    /** 当时实际字典值 */
    private String valueCode;
    /** 当时实际标签；不从当前字典倒填 */
    private String label;
    /** 原顺序 */
    private Integer sortOrder;
    /** 原件已保存的来源状态 */
    private String sourceStatusCode;
}
