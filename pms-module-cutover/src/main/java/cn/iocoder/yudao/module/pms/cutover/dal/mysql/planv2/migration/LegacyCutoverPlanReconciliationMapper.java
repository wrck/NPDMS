package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.query.LegacyCutoverPlanTargetQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LegacyCutoverPlanReconciliationMapper {

    CutoverTaskDO selectQualifiedTargetForUpdate(@Param("query") LegacyCutoverPlanTargetQuery query);

    Long selectIdentityConflictForUpdate(@Param("query") LegacyCutoverPlanTargetQuery query);
}
