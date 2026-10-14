package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverPlanStorageMapper {
    int deleteDraftDevices(@Param("query") CutoverPlanChildrenQuery query);
    int deleteDraftSchedules(@Param("query") CutoverPlanChildrenQuery query);
    int deleteDraftFiles(@Param("query") CutoverPlanChildrenQuery query);
    int deleteDraftRisks(@Param("query") CutoverPlanChildrenQuery query);
}
