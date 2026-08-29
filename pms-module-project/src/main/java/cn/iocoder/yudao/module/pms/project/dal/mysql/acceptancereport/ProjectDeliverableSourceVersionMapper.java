package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableCurrentSourceLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.PendingArchiveSourceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectDeliverableSourceVersionMapper extends BaseMapperX<ProjectDeliverableSourceVersionDO> {

    ProjectDeliverableSourceVersionDO selectCurrentForUpdate(
            @Param("query") DeliverableCurrentSourceLockQuery query);

    ProjectDeliverableSourceVersionDO selectIdentityForUpdate(
            @Param("query") DeliverableSourceIdentityQuery query);

    ProjectDeliverableSourceVersionDO selectByIdForUpdate(@Param("query") DeliverableSourceIdLockQuery query);

    java.util.List<ProjectDeliverableSourceVersionDO> selectPendingArchive(
            @Param("query") PendingArchiveSourceQuery query);
}
