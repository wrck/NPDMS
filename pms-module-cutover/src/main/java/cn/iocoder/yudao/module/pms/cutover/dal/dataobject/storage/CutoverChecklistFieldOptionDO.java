package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 字段选项。需求：CUT-07。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_field_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistFieldOptionDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 字段定义ID */
    private Long fieldDefinitionId;
    /** 选项顺序 */
    private Integer optionNo;
    /** 原选项标签 */
    private String label;
    /** 动态标量实际类型；只用于允许配置的元数据/参数 */
    private String valueTypeCode;
    /** 字符串型标量实际值 */
    private String valueText;
    /** 数字型标量实际值 */
    private BigDecimal valueNumber;
    /** 布尔型标量实际值 */
    private Boolean valueBoolean;
    /** 日期时间型标量实际值 */
    private LocalDateTime valueDatetime;
}
