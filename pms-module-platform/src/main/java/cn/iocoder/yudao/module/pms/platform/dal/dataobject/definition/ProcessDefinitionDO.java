package cn.iocoder.yudao.module.pms.platform.dal.dataobject.definition;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 中性过程定义：发布时冻结实体契约版本与操作版本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_plat_process_definition")
public class ProcessDefinitionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String definitionCode;
    /** 草稿为 0；每次发布取该编码下已发布最大版本 +1。 */
    private Integer definitionVersion;
    private String name;
    private String ownerModule;
    private String entityType;
    private String entityStableCode;
    private Integer entityContractVersion;
    private String operationCode;
    private Integer operationVersion;
    private String ruleCode;
    private String ruleVersion;
    /** 类型化字段条件 JSON（BusinessFieldFilter 数组）。 */
    private String conditionsJson;
    private String resultType;
    /** DRAFT / PUBLISHED */
    private String status;
    private LocalDateTime publishedAt;
}
