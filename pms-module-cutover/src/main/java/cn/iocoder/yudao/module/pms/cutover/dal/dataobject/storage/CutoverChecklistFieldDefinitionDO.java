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

/** 统一结构化字段定义。需求：CUT-03/07/09/10。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_field_definition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistFieldDefinitionDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 系统采集项既有定义修订ID */
    private Long itemDefinitionRevisionId;
    /** 任务自定义清单项ID */
    private Long customChecklistItemId;
    /** 父定义节点 */
    private Long parentFieldId;
    /** 修订内稳定元数据路径 */
    private String nodePath;
    /** ROOT、FIELD或COLUMN */
    private String nodeKindCode;
    /** 对外字段编码；兼容原code/key来源 */
    private String fieldCode;
    /** 原字段name */
    private String sourceName;
    /** 显示标签 */
    private String label;
    /** 控件/数据类型；包含TABLE */
    private String dataTypeCode;
    /** 是否必填 */
    private Boolean requiredFlag;
    /** 已有定义声明的长度限制 */
    private Integer maxLength;
    /** 已有受控选项/数据来源编码 */
    private String valueSourceCode;
    /** 节点顺序 */
    private Integer sortOrder;
    /** 服务端声明的固定业务语义键；不是SQL列名 */
    private String builtinFieldCode;
    /** 真正自定义字段键；不得占用内置键 */
    private String extensionFieldKey;
    /** 原visibleWhen.field对应的同对象字段路径 */
    private String visibleWhenFieldPath;
    /** 原equals的标量类型 */
    private String visibleEqualsTypeCode;
    /** 字符串相等条件 */
    private String visibleEqualsText;
    /** 数字相等条件 */
    private BigDecimal visibleEqualsNumber;
    /** 布尔相等条件 */
    private Boolean visibleEqualsBoolean;
    /** 日期时间相等条件 */
    private LocalDateTime visibleEqualsDatetime;
    /** 原检查来源文件 */
    private String sourceFile;
    /** 原检查来源页签 */
    private String sourceSheet;
    /** 原检查来源行 */
    private Integer sourceRow;
    /** 原检查项名称 */
    private String sourceOriginalName;
    /** 原拆分序号 */
    private Integer sourcePart;
    /** 是否由原条目拆分 */
    private Boolean sourceSplitFlag;
}
