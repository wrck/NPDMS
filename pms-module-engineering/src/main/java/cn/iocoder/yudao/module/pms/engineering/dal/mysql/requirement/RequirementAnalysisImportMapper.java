package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementProjectQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RequirementAnalysisImportMapper {
    List<RequirementAnalysisImportSource> lockSources(@Param("query") RequirementProjectQuery query);
    List<RequirementAnalysisRevisionDO> lockTargets(@Param("query") RequirementProjectQuery query);
    cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisDO lockCurrent(
            @Param("query") RequirementProjectQuery query);
}
