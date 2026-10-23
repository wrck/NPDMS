package cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ImplementationReadinessSourceMapper {
    List<InstallationDO> installations(@Param("query") ReadinessProjectQuery query);
    List<ConfigurationDO> configurations(@Param("query") ReadinessProjectQuery query);
    List<JointTestDO> jointTests(@Param("query") ReadinessProjectQuery query);
    List<SolutionDO> approvedSolutions(@Param("query") ReadinessProjectQuery query);
}
