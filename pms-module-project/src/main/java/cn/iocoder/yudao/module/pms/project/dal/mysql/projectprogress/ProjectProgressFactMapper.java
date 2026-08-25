package cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressFactDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Collection;
import java.util.List;
@Mapper
public interface ProjectProgressFactMapper extends BaseMapperX<ProjectProgressFactDO> {
    @Select("""
            <script>
            SELECT ranked.* FROM (
              SELECT f.*, ROW_NUMBER() OVER (
                PARTITION BY f.project_id
                ORDER BY f.occurred_at DESC, f.fact_version DESC, f.id DESC) AS row_num
              FROM proj_project_progress_fact f
              WHERE f.tenant_id = #{tenantId} AND f.deleted = b'0'
                AND f.project_id IN
                <foreach collection="projectIds" item="projectId" open="(" separator="," close=")">
                  #{projectId}
                </foreach>
            ) ranked WHERE ranked.row_num = 1
            </script>
            """)
    List<ProjectProgressFactDO> selectLatestByProjects(@Param("tenantId") Long tenantId,
                                                        @Param("projectIds") Collection<Long> projectIds);
}
