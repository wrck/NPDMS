package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectCustomerContactMapper extends BaseMapperX<ProjectCustomerContactDO> {
    default PageResult<ProjectCustomerContactDO> selectPage(ProjectContactPageQuery query) {
        return selectPage(query.page(), new LambdaQueryWrapperX<ProjectCustomerContactDO>()
                .eq(ProjectCustomerContactDO::getTenantId, query.tenantId())
                .eq(ProjectCustomerContactDO::getProjectId, query.projectId())
                .eqIfPresent(ProjectCustomerContactDO::getStatus, query.status())
                .likeIfPresent(ProjectCustomerContactDO::getName, query.name())
                .orderByDesc(ProjectCustomerContactDO::getPrimaryFlag)
                .orderByAsc(ProjectCustomerContactDO::getId));
    }
    ProjectCustomerContactDO selectForUpdate(@Param("query") ProjectContactRowQuery query);
    ProjectCustomerContactDO selectSourceIncludingDeletedForUpdate(@Param("query") ProjectContactSourceQuery query);
    ProjectCustomerContactDO selectPrimaryForUpdate(@Param("query") ProjectContactRowQuery query);
    int deleteByVersion(@Param("query") ProjectContactDeleteCommand query);
    ProjectCustomerContactDO selectIncludingDeletedForUpdate(@Param("query") ProjectContactRowQuery query);
    int restoreByVersion(@Param("query") ProjectContactRestoreCommand query);
    java.util.List<cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectContactHistoryState> selectHistoryStates(
            @Param("query") ProjectContactHistoryStateQuery query);
}
