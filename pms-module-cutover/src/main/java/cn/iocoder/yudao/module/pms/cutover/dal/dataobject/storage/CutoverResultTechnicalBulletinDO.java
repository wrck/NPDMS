package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 技术公告及逐条反馈结果行。需求：CUT-03/09。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_result_technical_bulletin")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverResultTechnicalBulletinDO {
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
    /** 原始公告所来自的查询；须在本结果采用的查询集合内 */
    private Long externalQueryId;
    /** 反馈版本引用的首次外部原始行；同本表自关联，不指向反馈行 */
    private Long sourceBulletinRowId;
    /** 正式来源提供的记录ID，不以行号伪造 */
    private String externalRecordId;
    /** 正式来源提供的源版本 */
    private String externalRecordVersion;
    /** 实际公告编号；外部原始值只读 */
    private String bulletinNumber;
    /** 实际公告主题；外部原始值只读 */
    private String bulletinSubject;
    /** 实际公告描述；外部原始值只读 */
    private String bulletinDescription;
    /** 实际解决方案；外部原始值只读 */
    private String bulletinSolution;
    /** 工程师填写值：本次是否涉及 */
    private Boolean involvedFlag;
    /** 工程师填写值：本次采取措施 */
    private String measures;
    /** 仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入 */
    private String extensionValues;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
