package cn.iocoder.yudao.module.pms.commerce.dal.mysql.associated;

import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectShipmentLogisticsApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectShipmentLogisticsMapper {
    List<ProjectShipmentLogisticsApi.Fact> selectFacts(@Param("query") ProjectShipmentLogisticsApi.Query query);
}
