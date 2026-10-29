package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.ProjectSerialExportRow;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectSerialExportQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectSerialExportMapper {
    List<ProjectSerialExportRow> selectBatch(@Param("query") ProjectSerialExportQuery query);
}
