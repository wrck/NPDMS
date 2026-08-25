package cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
@Mapper
public interface ProjectSplitRequestMapper extends BaseMapperX<ProjectSplitRequestDO> {
    default ProjectSplitRequestDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(new LambdaQueryWrapperX<ProjectSplitRequestDO>()
                .eq(ProjectSplitRequestDO::getId, id));
    }

    @Update("""
            UPDATE proj_project_split_request
            SET draft_version = draft_version + 1,
                template_revision_id = #{templateRevisionId},
                parent_version = #{parentVersion},
                scope_version = #{scopeVersion},
                tree_version = #{treeVersion},
                validation_status = NULL,
                validation_summary = NULL,
                preview_hash = NULL,
                validated_at = NULL,
                version = version + 1
            WHERE id = #{id} AND status = 'DRAFT'
              AND draft_version = #{expectedDraftVersion} AND deleted = b'0'
            """)
    int updateDraftIfMatch(@Param("id") Long id, @Param("expectedDraftVersion") Integer expectedDraftVersion,
                           @Param("templateRevisionId") Long templateRevisionId,
                           @Param("parentVersion") Integer parentVersion, @Param("scopeVersion") Long scopeVersion,
                           @Param("treeVersion") Long treeVersion);

    @Update("""
            UPDATE proj_project_split_request
            SET status = 'APPLIED', applied_change_batch_id = #{changeBatchId}, version = version + 1
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DRAFT'
              AND draft_version = #{expectedDraftVersion} AND deleted = b'0'
            """)
    int markAppliedIfMatch(@Param("tenantId") Long tenantId, @Param("id") Long id,
                           @Param("expectedDraftVersion") Integer expectedDraftVersion,
                           @Param("changeBatchId") String changeBatchId);
}
