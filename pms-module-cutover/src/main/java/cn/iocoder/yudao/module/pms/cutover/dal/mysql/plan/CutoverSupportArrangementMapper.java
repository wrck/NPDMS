package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverSupportContactUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverSupportArrangementMapper extends BaseMapperX<CutoverSupportArrangementDO> {
    List<CutoverSupportArrangementDO> selectListByPlan(@Param("query") CutoverPlanChildrenQuery query);
    List<CutoverSupportArrangementDO> selectListByPlanForUpdate(@Param("query") CutoverPlanChildrenQuery query);
    int deleteDraftRows(@Param("query") CutoverPlanChildrenQuery query);
    int updateApprovedContactIfMatch(@Param("query") CutoverSupportContactUpdate query);
}
