package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectDeviceOrganizationApi.Organization;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface DeviceProjectOrganizationMapper {
    List<Long> selectVisibleProjectIds(@Param("query") DeviceOrganizationProjectQuery query);
    List<Organization> selectOrganizations(@Param("query") DeviceProjectOrganizationListQuery query);
}
