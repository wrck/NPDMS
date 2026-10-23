package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 真正自定义的表格结果行。需求：CUT-03/07/10。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_result_custom_row")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverResultCustomRowDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 所属统一结果版本；与根同事务写入 */
    private Long checklistResultId;
    /** 整张业务表格的定义ID，即data_type_code=TABLE的FIELD/ROOT节点；不是单列定义 */
    private Long tableFieldDefinitionId;
    /** 同一结果、同一表格内的行号 */
    private Integer rowNo;
    /** 自定义嵌套表格的父行；同结果且无环 */
    private Long parentCustomRowId;
    /** 生成列；无父行为0，其余为父行ID；生成表达式 COALESCE(parent_custom_row_id,0) */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long parentCustomRowKey;
    /** 该自定义表格一行的真正自定义字段值；不装整张固定业务表 */
    private String extensionValues;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
