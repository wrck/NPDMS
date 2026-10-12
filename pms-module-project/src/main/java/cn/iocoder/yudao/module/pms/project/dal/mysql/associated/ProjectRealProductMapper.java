package cn.iocoder.yudao.module.pms.project.dal.mysql.associated;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectRealProductDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface ProjectRealProductMapper extends BaseMapperX<ProjectRealProductDO> {
    record RecordIdentityQuery(Long tenantId,Long projectId,Long id) {}
    @lombok.Builder
    record BusinessIdentityQuery(Long tenantId,Long projectId,String contractNo,String projectCode,String executionNo,String productCategoryName,String productName,String productSubCode,String productSubModel,String productSubName,java.math.BigDecimal quantity,String memo) {}
    record MigrationProjectQuery(Long tenantId,Long projectId) {}
    ProjectRealProductDO selectRecordForUpdate(@Param("query") RecordIdentityQuery query);
    List<ProjectRealProductDO> selectBusinessRecordsForUpdate(@Param("query") BusinessIdentityQuery query);
    Long countAllProjectRecords(@Param("query") MigrationProjectQuery query);
    List<ProjectRealProductDO> selectProjectPage(@Param("query") AssociatedPageQuery query);
    Long countProject(@Param("query") AssociatedPageQuery query);
}
