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

/** 外部参数与返回映射。需求：CUT-03/07/09。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_checklist_external_mapping")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistExternalMappingDO extends TenantBaseDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属采集项定义修订 */
    private Long itemDefinitionRevisionId;
    /** PARAM或RETURN */
    private String mappingDirectionCode;
    /** 映射顺序 */
    private Integer mappingNo;
    /** 受控上下文/外部响应字段路径 */
    private String sourcePath;
    /** 参数名或服务端声明的结果业务键 */
    private String targetKey;
    /** 正式契约声明的数据类型 */
    private String valueTypeCode;
    /** 是否必需 */
    private Boolean requiredFlag;
}
