package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactRevalidationQuery;

/** SOL需求分析已完成版本的公开只读事实。 */
public interface RequirementAnalysisFactApi {

    RequirementAnalysisFact inspect(RequirementAnalysisFactQuery query);

    RequirementAnalysisFact lockAndRevalidate(RequirementAnalysisFactRevalidationQuery query);
}
