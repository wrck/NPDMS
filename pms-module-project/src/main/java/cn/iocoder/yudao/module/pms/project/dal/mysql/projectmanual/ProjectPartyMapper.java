package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectPartyQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface ProjectPartyMapper extends BaseMapperX<ProjectPartyDO> {
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
