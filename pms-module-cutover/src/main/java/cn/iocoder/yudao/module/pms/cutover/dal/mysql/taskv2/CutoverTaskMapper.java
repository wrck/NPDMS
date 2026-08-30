package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskAssessmentLinkUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskTransitionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverTaskMapper extends BaseMapperX<CutoverTaskDO> {
    CutoverTaskDO selectForUpdate(@Param("query") CutoverTaskRowQuery query);
    int linkAssessmentIfMatch(@Param("query") CutoverTaskAssessmentLinkUpdate query);
    int transitionIfMatch(@Param("query") CutoverTaskTransitionUpdate query);
}
