package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverAssessmentSubmitUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverAssessmentMapper extends BaseMapperX<CutoverAssessmentDO> {
    CutoverAssessmentDO selectForUpdate(@Param("query") CutoverAssessmentRowQuery query);
    int updateDraftIfMatch(@Param("query") CutoverAssessmentDraftUpdate query);
    int submitIfMatch(@Param("query") CutoverAssessmentSubmitUpdate query);
    int deleteDraftDeviceFacts(@Param("query") CutoverAssessmentRowQuery query);
    int invalidateForRevisionIfMatch(@Param("query") cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverRevisionInvalidationUpdate query);
}
