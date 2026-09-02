package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverAssessmentSubmitUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverAssessmentMapper extends BaseMapperX<CutoverAssessmentDO> {
    CutoverAssessmentDO selectForUpdate(@Param("query") CutoverAssessmentRowQuery query);
    int updateDraftIfMatch(@Param("query") CutoverAssessmentDraftUpdate query);
    int submitIfMatch(@Param("query") CutoverAssessmentSubmitUpdate query);
}
