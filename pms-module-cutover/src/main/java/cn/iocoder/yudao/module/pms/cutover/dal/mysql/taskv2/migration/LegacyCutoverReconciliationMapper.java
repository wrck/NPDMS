package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.migration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.migration.query.LegacyCutoverReconciliationQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LegacyCutoverReconciliationMapper {

    CutTaskDO selectSourceForUpdate(@Param("query") LegacyCutoverReconciliationQuery query);

    long countTargetIdentityConflicts(@Param("query") LegacyCutoverReconciliationQuery query);
}
