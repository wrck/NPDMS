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
            var policy=new CollectionLogFilePolicy(tasks,projects,permissions,mock(org.springframework.beans.factory.ObjectProvider.class));var task=new CollectionTaskDO();task.setProjectId("21");task.setResultVersion(1L);task.setSourceContext(source[0]);task.setSourceObjectType(source[1]);
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
    @Test void inspectionFileCannotBypassItsOwnerWithProjectPermissionAlone() {
        var tasks = mock(CollectionTaskMapper.class); var projects = mock(ProjectScopeApi.class); var permissions = mock(PermissionApi.class);
        @SuppressWarnings("unchecked") var sources = (org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter>) mock(org.springframework.beans.factory.ObjectProvider.class);
        var adapter = mock(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter.class);
        when(adapter.entry()).thenReturn("inspection"); when(sources.orderedStream()).thenAnswer(call -> java.util.stream.Stream.of(adapter));
        var policy = new CollectionLogFilePolicy(tasks, projects, permissions, sources);
        var task = new CollectionTaskDO(); task.setProjectId("21"); task.setDeviceId("31"); task.setSourceObjectId("41");
        task.setResultVersion(1L); task.setSourceContext("SRV"); task.setSourceObjectType("INSPECTION_EXECUTION");
        when(tasks.selectByTenantAndPlatformTaskId(1L, "task")).thenReturn(task);
        when(permissions.hasAnyPermissions(7L, "pms:inspection-task:query")).thenReturn(true);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L, 1L, Set.of(21L), Set.of()));
        var query = new FileBusinessObjectPolicyQuery(1L,7L,"PLT","CollectionTask","task","COLLECTION_LOG","result-1","DOWNLOAD");
        assertFalse(policy.inspect(query).allowed());
        when(adapter.authorize(any())).thenReturn(cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter.Source.builder()
                .sourceContext("SRV").sourceObjectType("INSPECTION_EXECUTION").projectId(21L).deviceId(31L).build());
        assertTrue(policy.inspect(query).allowed());
        when(adapter.authorize(any())).thenThrow(new IllegalStateException("owner access denied"));
        assertThrows(IllegalStateException.class, () -> policy.inspect(query));
    }
}
