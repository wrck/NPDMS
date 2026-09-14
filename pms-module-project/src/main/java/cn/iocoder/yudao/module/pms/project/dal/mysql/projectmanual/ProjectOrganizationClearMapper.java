package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.module.system.api.organization.OrganizationClearGuard;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectOrganizationClearMapper {
    long selectReferenceCount(OrganizationClearGuard.Scope query);
}
