package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectPartyQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProjectPartyMapper extends BaseMapperX<ProjectPartyDO> {
    record PartySourceQuery(Long tenantId, String sourceSystem, String sourceTable,
                            String sourceRecordKey, String partyRole) {}
    ProjectPartyDO selectSourceForUpdate(@Param("query") PartySourceQuery query);
    record HeaderFinalCustomerQuery(Long tenantId, Long projectId) {}
    default List<ProjectPartyDO> selectHeaderFinalCustomers(HeaderFinalCustomerQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectPartyDO>()
                .eq(ProjectPartyDO::getTenantId, query.tenantId())
                .eq(ProjectPartyDO::getProjectId, query.projectId())
                .eq(ProjectPartyDO::getPartyRole, "FINAL_CUSTOMER")
                .eq(ProjectPartyDO::getSourceSystem, "DPPMS")
                .eq(ProjectPartyDO::getSourceTable, "pm_project_header")
                .orderByAsc(ProjectPartyDO::getId));
    }
    default List<ProjectPartyDO> selectCurrent(ProjectPartyQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectPartyDO>()
            .eq(ProjectPartyDO::getTenantId, query.tenantId())
            .eq(ProjectPartyDO::getProjectId, query.projectId())
            .eq(ProjectPartyDO::getStatus, "ACTIVE")
            .and(q -> q.isNull(ProjectPartyDO::getEffectiveFrom).or().le(ProjectPartyDO::getEffectiveFrom, query.at()))
            .and(q -> q.isNull(ProjectPartyDO::getEffectiveTo).or().gt(ProjectPartyDO::getEffectiveTo, query.at()))
            .orderByAsc(ProjectPartyDO::getPartyRole, ProjectPartyDO::getId));
    }
}
