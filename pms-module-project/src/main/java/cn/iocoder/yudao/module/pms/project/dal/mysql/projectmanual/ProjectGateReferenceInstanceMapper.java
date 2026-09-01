package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 项目门禁实例引用行 Mapper（F-PM01 / V57）
 */
@Mapper
public interface ProjectGateReferenceInstanceMapper extends BaseMapperX<ProjectGateReferenceInstanceDO> {

    /**
     * 按门禁实例ID集合查询引用行
     */
    default List<ProjectGateReferenceInstanceDO> selectListByGateIds(Collection<Long> gateIds) {
        return selectList(ProjectGateReferenceInstanceDO::getGateId, gateIds);
    }

    List<ProjectGateReferenceInstanceDO> selectOrdered(@Param("query") ProjectGateReferenceForUpdateQuery query);

    List<ProjectGateReferenceInstanceDO> selectOrderedForUpdate(
            @Param("query") ProjectGateReferenceForUpdateQuery query);
}
