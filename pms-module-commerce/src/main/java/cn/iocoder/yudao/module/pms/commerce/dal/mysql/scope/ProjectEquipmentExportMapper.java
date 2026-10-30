package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ProjectEquipmentExportQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ProjectEquipmentExportSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectEquipmentExportMapper {
    List<ProjectEquipmentExportSource> selectBatch(@Param("query") ProjectEquipmentExportQuery query);
}
