package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisCompleteUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisContentUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisDynamicContentUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisEffectiveClearUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisHistoryQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RequirementAnalysisRootMapper {
    int insertDynamicRoot(@Param("row") PreparationDO row);
    PreparationDO selectDraft(@Param("query") RequirementAnalysisProjectQuery query);
    PreparationDO selectDraftForUpdate(@Param("query") RequirementAnalysisProjectQuery query);
    PreparationDO selectEffective(@Param("query") RequirementAnalysisProjectQuery query);
    PreparationDO selectEffectiveForUpdate(@Param("query") RequirementAnalysisProjectQuery query);
    PreparationDO selectById(@Param("query") RequirementAnalysisRowQuery query);
    PreparationDO selectForUpdate(@Param("query") RequirementAnalysisRowQuery query);
    List<PreparationDO> selectCompletedHistory(@Param("query") RequirementAnalysisHistoryQuery query);
    int incrementContentIfMatch(@Param("update") RequirementAnalysisContentUpdate update);
    int incrementDynamicContentIfMatch(@Param("update") RequirementAnalysisDynamicContentUpdate update);
    int clearEffectiveIfMatch(@Param("update") RequirementAnalysisEffectiveClearUpdate update);
    int completeDraftIfMatch(@Param("update") RequirementAnalysisCompleteUpdate update);
}
