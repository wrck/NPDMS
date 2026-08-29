package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.projection.AcceptanceReportFileScope;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceCurrentReportLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceReportFileScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceNextReportVersionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AcceptanceReportVersionMapper extends BaseMapperX<AcceptanceReportVersionDO> {

    AcceptanceReportFileScope selectFileScope(@Param("query") AcceptanceReportFileScopeQuery query);

    Integer selectNextVersionNo(@Param("query") AcceptanceNextReportVersionQuery query);

    AcceptanceReportVersionDO selectCurrentForUpdate(@Param("query") AcceptanceCurrentReportLockQuery query);

    AcceptanceReportVersionDO selectByIdForUpdate(@Param("query") AcceptanceReportIdLockQuery query);
}
