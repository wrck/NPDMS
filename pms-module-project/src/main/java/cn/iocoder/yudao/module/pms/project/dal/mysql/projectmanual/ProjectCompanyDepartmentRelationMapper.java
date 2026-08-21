package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCompanyDepartmentRelationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目组织关系 Mapper（F-PM01 / V57；V1 承载下单办事处 ORDER_OFFICE）
 */
@Mapper
public interface ProjectCompanyDepartmentRelationMapper extends BaseMapperX<ProjectCompanyDepartmentRelationDO> {
}
