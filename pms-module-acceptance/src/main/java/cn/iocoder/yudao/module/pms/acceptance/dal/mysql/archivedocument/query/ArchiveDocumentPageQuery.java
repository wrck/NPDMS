package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveDocumentPageQuery extends PageParam {

    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private Long projectId;
    private String code;
    private String name;
    private String documentType;
    private Integer status;

}
