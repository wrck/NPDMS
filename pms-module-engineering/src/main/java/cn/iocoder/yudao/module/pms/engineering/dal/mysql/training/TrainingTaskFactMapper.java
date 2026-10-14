package cn.iocoder.yudao.module.pms.engineering.dal.mysql.training;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingTaskFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingTaskPageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Only public record identity and completion state; customer token/signature data are not task facts. */
@Mapper
public interface TrainingTaskFactMapper extends BaseMapperX<TrainingDO> {
    default TrainingDO selectTaskFact(TrainingTaskFactQuery query) {
        return selectOne(new LambdaQueryWrapperX<TrainingDO>()
                .select(TrainingDO::getId, TrainingDO::getTenantId, TrainingDO::getProjectId,
                        TrainingDO::getName, TrainingDO::getStatus, TrainingDO::getVersion, TrainingDO::getDeleted)
                .eq(TrainingDO::getTenantId, query.tenantId()).eq(TrainingDO::getProjectId, query.projectId())
                .eq(TrainingDO::getId, query.trainingId()));
    }
    TrainingDO selectTaskFactForUpdate(@Param("query") TrainingTaskFactQuery query);
    default List<TrainingDO> selectAssociationPage(TrainingTaskPageQuery query) {
        return selectPage(new Page<TrainingDO>(1, query.pageSize(), false), new LambdaQueryWrapperX<TrainingDO>()
                .select(TrainingDO::getId, TrainingDO::getTenantId, TrainingDO::getProjectId,
                        TrainingDO::getName, TrainingDO::getStatus, TrainingDO::getVersion, TrainingDO::getDeleted)
                .eq(TrainingDO::getTenantId, query.tenantId()).eq(TrainingDO::getProjectId, query.projectId())
                .gt(query.afterId() != null, TrainingDO::getId, query.afterId()).orderByAsc(TrainingDO::getId)).getRecords();
    }
}
