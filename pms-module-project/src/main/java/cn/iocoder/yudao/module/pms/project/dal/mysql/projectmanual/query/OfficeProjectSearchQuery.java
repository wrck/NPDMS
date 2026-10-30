package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi.Organization;
import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Set;

@Value
@Builder
public class OfficeProjectSearchQuery {
    Long tenantId;
    List<Organization> organizations;
    String exactProjectName;
    String exactContractNo;
    Set<Long> contractProjectIds;
    Long projectId;
    PageParam pageParam;
}
