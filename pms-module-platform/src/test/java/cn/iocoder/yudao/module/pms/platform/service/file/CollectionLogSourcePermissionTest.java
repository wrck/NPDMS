package cn.iocoder.yudao.module.pms.platform.service.file;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CollectionLogSourcePermissionTest {
    @Test void eachSourceUsesItsOwnQueryPermissionAndProjectScope(){
        for(var source: new String[][]{{"IMP","Configuration","pms:imp-configuration:query"},{"IMP","JointTest","pms:imp-joint-test:query"},{"PLT","CollectionCenter","pms:device-collection:query"}}){
            var tasks=mock(CollectionTaskMapper.class);var projects=mock(ProjectScopeApi.class);var permissions=mock(PermissionApi.class);
            var policy=new CollectionLogFilePolicy(tasks,projects,permissions);var task=new CollectionTaskDO();task.setProjectId("21");task.setResultVersion(1L);task.setSourceContext(source[0]);task.setSourceObjectType(source[1]);
            when(tasks.selectByTenantAndPlatformTaskId(1L,"task")).thenReturn(task);
            when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(21L),Set.of()));
            var query=new FileBusinessObjectPolicyQuery(1L,7L,"PLT","CollectionTask","task","COLLECTION_LOG","result-1","DOWNLOAD");
            assertFalse(policy.inspect(query).allowed());
            when(permissions.hasAnyPermissions(7L,source[2])).thenReturn(true);
            assertTrue(policy.inspect(query).allowed());
            when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(),Set.of()));
            assertFalse(policy.inspect(query).allowed());
        }
    }
}
