package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogVersionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/** Technical version cursor; history rows remain immutable. The device Owner is locked before allocation. */
@Mapper public interface DeviceLogVersionMapper {
    int allocate(@Param("query") DeviceLogVersionQuery query);
    Long current(@Param("query") DeviceLogVersionQuery query);
}
