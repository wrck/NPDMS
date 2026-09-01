package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSuccessorQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanVersionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverPlanRevisionMapper extends BaseMapperX<CutoverPlanRevisionDO> {
    CutoverPlanRevisionDO selectCurrent(@Param("query") CutoverPlanRevisionQuery query);
    List<CutoverPlanRevisionDO> selectListLegacyByTask(@Param("query") CutoverPlanRevisionQuery query);
    CutoverPlanRevisionDO selectCurrentForUpdate(@Param("query") CutoverPlanRevisionQuery query);
    List<CutoverPlanRevisionDO> selectListDirectSuccessors(@Param("query") CutoverPlanSuccessorQuery query);
    List<CutoverPlanRevisionDO> selectListHistory(@Param("query") CutoverPlanHistoryQuery query);
    Integer selectMaxRevisionNo(@Param("query") CutoverPlanHistoryQuery query);
    int replaceDraftIfMatch(@Param("query") CutoverPlanDraftUpdate query);
    int advanceDraftVersionIfMatch(@Param("query") CutoverPlanVersionUpdate query);
    int submitDraftIfMatch(@Param("query") CutoverPlanSubmitUpdate query);
    int invalidateSubmittedIfMatch(@Param("query") CutoverPlanInvalidationUpdate query);
}
