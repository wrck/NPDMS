package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverPlanStepMapper extends BaseMapperX<CutoverPlanStepDO> {
    List<CutoverPlanStepDO> selectListByPlan(@Param("query") CutoverPlanChildrenQuery query);
    List<CutoverPlanStepDO> selectListByPlanForUpdate(@Param("query") CutoverPlanChildrenQuery query);
    int deleteDraftRows(@Param("query") CutoverPlanChildrenQuery query);
}
