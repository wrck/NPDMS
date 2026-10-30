package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverApprovedContactVersionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanReplacementUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanSuccessorQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanVersionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverFrozenPlanFileQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverPlanRevisionMapper extends BaseMapperX<CutoverPlanRevisionDO> {
    CutoverPlanRevisionDO selectCurrent(@Param("query") CutoverPlanRevisionQuery query);
    CutoverPlanRevisionDO selectCurrentForUpdate(@Param("query") CutoverPlanRevisionQuery query);
    CutoverPlanRevisionDO selectByIdForUpdate(@Param("query") CutoverPlanRevisionQuery query);
    List<CutoverPlanRevisionDO> selectListDirectSuccessors(@Param("query") CutoverPlanSuccessorQuery query);
    List<CutoverPlanRevisionDO> selectListHistory(@Param("query") CutoverPlanHistoryQuery query);
    boolean existsFrozenFileVersion(@Param("query") CutoverFrozenPlanFileQuery query);
    Integer selectMaxRevisionNo(@Param("query") CutoverPlanHistoryQuery query);
    int replaceDraftIfMatch(@Param("query") CutoverPlanDraftUpdate query);
    int advanceDraftVersionIfMatch(@Param("query") CutoverPlanVersionUpdate query);
    int advanceApprovedVersionIfMatch(@Param("query") CutoverApprovedContactVersionUpdate query);
    int replaceSubmittedIfMatch(@Param("query") CutoverPlanReplacementUpdate query);
    int submitDraftIfMatch(@Param("query") CutoverPlanSubmitUpdate query);
    int invalidateSubmittedIfMatch(@Param("query") CutoverPlanInvalidationUpdate query);
    int invalidateForRevisionIfMatch(@Param("query") cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverRevisionInvalidationUpdate query);
}
