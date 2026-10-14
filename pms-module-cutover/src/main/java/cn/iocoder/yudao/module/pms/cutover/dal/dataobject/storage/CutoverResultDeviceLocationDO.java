package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 设备部署位置结果行。需求：CUT-03/10。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_result_device_location")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverResultDeviceLocationDO {
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
    /** 填写值：设备类型 */
    private String deviceType;
    /** 填写值：序列号 */
    private String sn;
    /** 填写值：设备名称 */
    private String deviceName;
    /** 填写值：部署/安装位置 */
    private String location;
    /** 仅本行经精确定义声明的真正自定义列值；所有固定列、文件关系、来源身份禁止进入 */
    private String extensionValues;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
