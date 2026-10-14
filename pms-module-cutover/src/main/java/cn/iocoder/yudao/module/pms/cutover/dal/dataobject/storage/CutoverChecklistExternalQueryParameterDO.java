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

/** 外部查询实际参数。需求：CUT-03/07/09。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_external_query_parameter")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverChecklistExternalQueryParameterDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 所属查询 */
    private Long externalQueryId;
    /** 使用的配置映射 */
    private Long mappingId;
    /** 正式接口参数名 */
    private String parameterName;
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
