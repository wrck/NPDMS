package cn.iocoder.yudao.module.pms.project.dal.mysql.associated;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectProductConfigurationDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface ProjectProductConfigurationMapper extends BaseMapperX<ProjectProductConfigurationDO> {
    ProjectProductConfigurationDO selectRecordForUpdate(@Param("query") ProjectProductConfigurationRecordQuery query);
    List<ProjectProductConfigurationDO> selectBusinessRecordsForUpdate(@Param("query") ProjectProductConfigurationBusinessQuery query);
    Long countAllProjectRecords(@Param("query") ProjectProductConfigurationProjectQuery query);
    List<ProjectProductConfigurationDO> selectProjectPage(@Param("query") ProjectDisplayCachePageQuery query);
    Long countProject(@Param("query") ProjectDisplayCachePageQuery query);
    List<ProjectProductConfigurationDO> selectPathNodes(@Param("query") ProjectConfigurationPathQuery query);
}
