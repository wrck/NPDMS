package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 清单匹配输入维度。需求：CUT-03。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_input_dimension")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverChecklistInputDimensionDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 清单修订ID */
    private Long checklistId;
    /** 维度定义ID */
    private Long configurationDimensionId;
    /** 原维度编码 */
    private String dimensionCode;
    /** 同维度值次序 */
    private Integer valueNo;
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
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
