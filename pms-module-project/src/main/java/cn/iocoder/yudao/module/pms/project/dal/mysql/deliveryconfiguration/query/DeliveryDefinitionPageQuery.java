package cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** PM-03: filtered definition revision history. */
@Data @EqualsAndHashCode(callSuper = true)
public class DeliveryDefinitionPageQuery extends PageParam {
    private Long tenantId;
    private String definitionKind;
    private String definitionCode;
    private String revisionState;
}
