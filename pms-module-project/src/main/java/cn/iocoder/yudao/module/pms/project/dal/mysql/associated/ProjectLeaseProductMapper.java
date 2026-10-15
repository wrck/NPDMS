package cn.iocoder.yudao.module.pms.project.dal.mysql.associated;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectLeaseProductDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface ProjectLeaseProductMapper extends BaseMapperX<ProjectLeaseProductDO> {
    ProjectLeaseProductDO selectRecordForUpdate(@Param("query") ProjectLeaseProductRecordQuery query);
    List<ProjectLeaseProductDO> selectBusinessRecordsForUpdate(@Param("query") ProjectLeaseProductBusinessQuery query);
    Long countAllProjectRecords(@Param("query") ProjectLeaseProductProjectQuery query);
    List<ProjectLeaseProductDO> selectProjectPage(@Param("query") ProjectDisplayCachePageQuery query);
    Long countProject(@Param("query") ProjectDisplayCachePageQuery query);
}
