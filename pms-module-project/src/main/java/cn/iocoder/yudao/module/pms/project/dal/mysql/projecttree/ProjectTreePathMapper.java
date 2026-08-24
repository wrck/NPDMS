package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectTreePathMapper extends BaseMapperX<ProjectTreePathDO> {

    default List<ProjectTreePathDO> selectByAncestor(Long rootId, Long treeVersion,
                                                     Long ancestorId, Integer distance) {
        return selectList(new LambdaQueryWrapperX<ProjectTreePathDO>()
                .eq(ProjectTreePathDO::getRootProjectId, rootId)
                .eq(ProjectTreePathDO::getTreeVersion, treeVersion)
                .eq(ProjectTreePathDO::getAncestorProjectId, ancestorId)
                .eqIfPresent(ProjectTreePathDO::getDistance, distance)
                .orderByAsc(ProjectTreePathDO::getDistance, ProjectTreePathDO::getDescendantProjectId));
    }

    @Select("""
            <script>
            SELECT p.*
            FROM proj_project_tree_path t
            JOIN proj_project p ON p.id = t.descendant_project_id
              AND p.tenant_id = t.tenant_id AND p.deleted = b'0'
            WHERE t.tenant_id = #{tenantId} AND t.deleted = b'0'
              AND t.root_project_id = #{rootId} AND t.tree_version = #{treeVersion}
              AND t.ancestor_project_id = #{ancestorId}
              <if test="directOnly">AND t.distance = 1</if>
              <if test="directOnly == false">AND t.distance &gt; 0</if>
            ORDER BY p.tree_depth, p.tree_sort, p.id
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<ProjectMasterDO> selectDescendantsPage(@Param("tenantId") Long tenantId,
                                                @Param("rootId") Long rootId,
                                                @Param("treeVersion") Long treeVersion,
                                                @Param("ancestorId") Long ancestorId,
                                                @Param("directOnly") boolean directOnly,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM proj_project_tree_path t
            JOIN proj_project p ON p.id = t.ancestor_project_id
              AND p.tenant_id = t.tenant_id AND p.deleted = b'0'
            WHERE t.tenant_id = #{tenantId} AND t.deleted = b'0'
              AND t.root_project_id = #{rootId} AND t.tree_version = #{treeVersion}
              AND t.descendant_project_id = #{descendantId}
              <if test="includeSelf == false">AND t.distance &gt; 0</if>
            ORDER BY t.distance DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<ProjectMasterDO> selectPathPage(@Param("tenantId") Long tenantId,
                                         @Param("rootId") Long rootId,
                                         @Param("treeVersion") Long treeVersion,
                                         @Param("descendantId") Long descendantId,
                                         @Param("includeSelf") boolean includeSelf,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    @Select("""
            SELECT p.*
            FROM proj_project_tree_path t
            JOIN proj_project p ON p.id = t.descendant_project_id
              AND p.tenant_id = t.tenant_id AND p.deleted = b'0'
            WHERE t.tenant_id = #{tenantId} AND t.deleted = b'0'
              AND t.root_project_id = #{rootId} AND t.tree_version = #{treeVersion}
              AND t.distance = 0 AND p.business_level_code = #{businessLevelCode}
            ORDER BY p.tree_depth, p.tree_sort, p.id
            LIMIT #{offset}, #{limit}
            """)
    List<ProjectMasterDO> selectBusinessLevelPage(@Param("tenantId") Long tenantId,
                                                  @Param("rootId") Long rootId,
                                                  @Param("treeVersion") Long treeVersion,
                                                  @Param("businessLevelCode") String businessLevelCode,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);
}
