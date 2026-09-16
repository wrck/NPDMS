package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RequirementAnalysisMapper {
    RequirementAnalysisDO selectCurrent(@Param("query") RequirementEntityQuery query);
    RequirementAnalysisDO lockCurrent(@Param("query") RequirementEntityQuery query);
    RequirementAnalysisRevisionDO selectRevision(@Param("query") RequirementRevisionQuery query);
    RequirementAnalysisRevisionDO lockRevision(@Param("query") RequirementRevisionQuery query);
    RequirementAnalysisRevisionDO selectDraft(@Param("query") RequirementProjectQuery query);
    RequirementAnalysisRevisionDO selectEffective(@Param("query") RequirementProjectQuery query);
    RequirementAnalysisRevisionDO selectLatest(@Param("query") RequirementProjectQuery query);
    RequirementAnalysisRevisionDO selectLatestForEntity(@Param("query") RequirementEntityQuery query);
    int maxRevisionNo(@Param("query") RequirementEntityQuery query);
    List<RequirementAnalysisRevisionDO> selectHistory(@Param("query") RequirementRevisionPageQuery query);
    int insertCurrent(@Param("row") RequirementAnalysisDO row);
    int updateCurrent(@Param("row") RequirementAnalysisDO row);
    int insertRevision(@Param("row") RequirementAnalysisRevisionDO row);
    int saveDraft(@Param("row") RequirementAnalysisRevisionDO row);
    int freeze(@Param("update") RequirementFreezeUpdate update);
    int clearEffective(@Param("update") RequirementActivationUpdate update);
    int makeEffective(@Param("update") RequirementActivationUpdate update);
}
