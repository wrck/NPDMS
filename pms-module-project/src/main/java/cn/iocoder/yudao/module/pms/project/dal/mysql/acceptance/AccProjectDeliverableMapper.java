package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.DeliverableCurrentSourceClearQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdentityLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccProjectDeliverableMapper extends BaseMapperX<AccProjectDeliverableDO> {

    default Long selectCountByProjectId(Long projectId) {
        return selectCount(new LambdaQueryWrapperX<AccProjectDeliverableDO>()
                .eq(AccProjectDeliverableDO::getProjectId, projectId));
    }

    default List<AccProjectDeliverableDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<AccProjectDeliverableDO>()
                .eq(AccProjectDeliverableDO::getProjectId, projectId)
                .orderByAsc(AccProjectDeliverableDO::getStageCode, AccProjectDeliverableDO::getDeliverableCode));
    }

    AccProjectDeliverableDO selectByProjectAndCodeForUpdate(
            @Param("query") ProjectDeliverableIdentityLockQuery query);

    AccProjectDeliverableDO selectByIdForUpdate(@Param("query") ProjectDeliverableIdLockQuery query);

    int clearCurrentSource(@Param("query") DeliverableCurrentSourceClearQuery query);
}
