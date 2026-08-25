package cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDetailDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface ProjectProgressSnapshotDetailMapper extends BaseMapperX<ProjectProgressSnapshotDetailDO> {
    default List<ProjectProgressSnapshotDetailDO> selectBySnapshotId(Long snapshotId) {
        return selectList(new LambdaQueryWrapperX<ProjectProgressSnapshotDetailDO>()
                .eq(ProjectProgressSnapshotDetailDO::getSnapshotId, snapshotId)
                .orderByAsc(ProjectProgressSnapshotDetailDO::getChildProjectId));
    }
}
