package cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Collection;
import java.util.List;
@Mapper
public interface ProjectProgressSnapshotMapper extends BaseMapperX<ProjectProgressSnapshotDO> {
    default ProjectProgressSnapshotDO selectLatestByProject(Long projectId) {
        return selectOne(new LambdaQueryWrapperX<ProjectProgressSnapshotDO>()
                .eq(ProjectProgressSnapshotDO::getProjectId, projectId)
                .orderByDesc(ProjectProgressSnapshotDO::getCalculatedAt, ProjectProgressSnapshotDO::getId)
                .last("LIMIT 1"));
    }

    default ProjectProgressSnapshotDO selectByIdentity(Long projectId, Long policyRevisionId,
                                                       Long treeVersion, String sourceWatermark) {
        return selectOne(new LambdaQueryWrapperX<ProjectProgressSnapshotDO>()
                .eq(ProjectProgressSnapshotDO::getProjectId, projectId)
                .eq(ProjectProgressSnapshotDO::getPolicyRevisionId, policyRevisionId)
                .eq(ProjectProgressSnapshotDO::getTreeVersion, treeVersion)
                .eq(ProjectProgressSnapshotDO::getSourceWatermark, sourceWatermark));
    }

    @Select("""
            <script>
            SELECT ranked.* FROM (
              SELECT s.*, ROW_NUMBER() OVER (
                PARTITION BY s.project_id ORDER BY s.calculated_at DESC, s.id DESC) AS row_num
              FROM proj_project_progress_snapshot s
              WHERE s.tenant_id = #{tenantId} AND s.deleted = b'0'
                AND s.project_id IN
                <foreach collection="projectIds" item="projectId" open="(" separator="," close=")">
                  #{projectId}
                </foreach>
            ) ranked WHERE ranked.row_num = 1
            </script>
            """)
    List<ProjectProgressSnapshotDO> selectLatestByProjects(@Param("tenantId") Long tenantId,
                                                            @Param("projectIds") Collection<Long> projectIds);
}
