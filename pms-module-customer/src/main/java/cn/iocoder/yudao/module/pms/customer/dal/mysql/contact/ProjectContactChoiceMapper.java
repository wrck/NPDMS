package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ProjectContactChoiceMapper extends BaseMapperX<ProjectCustomerContactDO> {
    default List<ProjectCustomerContactDO> enabled(ProjectContactChoiceQuery query) {
        return selectList(new LambdaQueryWrapperX<ProjectCustomerContactDO>()
                .eq(ProjectCustomerContactDO::getTenantId, query.tenantId()).eq(ProjectCustomerContactDO::getProjectId, query.projectId())
                .eq(ProjectCustomerContactDO::getStatus, 0).orderByDesc(ProjectCustomerContactDO::getPrimaryFlag)
                .orderByAsc(ProjectCustomerContactDO::getId));
    }
    record ProjectContactChoiceQuery(Long tenantId, Long projectId) { }
}
