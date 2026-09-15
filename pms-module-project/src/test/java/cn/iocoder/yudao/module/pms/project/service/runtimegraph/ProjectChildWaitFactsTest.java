package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectChildClosureFactsQuery;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectChildWaitFactsTest {
    final ProjectTreeVersionMapper versions = mock(ProjectTreeVersionMapper.class);
    final ProjectTreeHierarchyMapper paths = mock(ProjectTreeHierarchyMapper.class);
    final ProjectChildClosureMapper projects = mock(ProjectChildClosureMapper.class);
    final ProjectChildWaitFacts service = new ProjectChildWaitFacts(versions, paths, projects);
    final ProjectMasterDO parent = child(9L, "ACTIVE");
    @BeforeEach void setup() {
        parent.setRootId(1L);
        var version = new ProjectTreeVersionDO(); version.setTenantId(7L); version.setTreeVersion(4L);
        when(versions.selectLatestActive(1L)).thenReturn(version);
        when(paths.selectWaitSubtree(any())).thenReturn(List.of(path(9L, 0), path(10L, 1), path(11L, 2)));
        when(projects.selectClosureFacts(any())).thenAnswer(call -> {
            ProjectChildClosureFactsQuery query = call.getArgument(0);
            return query.projectIds().stream().sorted().map(id -> child(id, id == 10L ? "NORMAL_CLOSED" : "ACTIVE")).toList();
        });
    }
    @Test void directChildrenExcludeGrandchildrenAndDescendantsIncludeThem() {
        assertEquals(true, service.resolve(parent, parameters("DIRECT")).value());
        assertEquals(false, service.resolve(parent, parameters("DESCENDANTS")).value());
        verify(projects).selectClosureFacts(new ProjectChildClosureFactsQuery(7L, Set.of(10L)));
        verify(projects).selectClosureFacts(new ProjectChildClosureFactsQuery(7L, Set.of(10L, 11L)));
        verify(paths, times(2)).selectWaitSubtree(argThat(q -> q.tenantId() == 7L && q.projectId() == 9L && q.treeVersion() == 4L));
    }
    @Test void emptySelfProjectionIsKnownButMissingProjectionIsUnknown() {
        when(paths.selectWaitSubtree(any())).thenReturn(List.of(path(9L, 0)));
        assertEquals(true, service.resolve(parent, parameters("DIRECT")).value());
        when(paths.selectWaitSubtree(any())).thenReturn(List.of());
        assertFalse(service.resolve(parent, parameters("DIRECT")).available());
        when(versions.selectLatestActive(1L)).thenReturn(null);
        assertFalse(service.resolve(parent, parameters("DIRECT")).available());
    }
    @Test void missingCrossTenantOrUnreadableChildIsNotSilentlyRemovedFromTheRange() {
        doReturn(List.of()).when(projects).selectClosureFacts(any());
        assertFalse(service.resolve(parent, parameters("DIRECT")).available());
        var foreign = child(10L, "NORMAL_CLOSED"); foreign.setTenantId(8L);
        when(projects.selectClosureFacts(any())).thenReturn(List.of(foreign));
        assertFalse(service.resolve(parent, parameters("DIRECT")).available());
        when(projects.selectClosureFacts(any())).thenThrow(new IllegalStateException("unavailable"));
        assertFalse(service.resolve(parent, parameters("DIRECT")).available());
    }
    static tools.jackson.databind.JsonNode parameters(String scope) {
        return JsonUtils.parseTree("""
                {"scope":"%s","acceptedClosureTypes":["NORMAL_CLOSED","EXCEPTION_CLOSED"],"quantifier":"ALL","emptyResult":true}
                """.formatted(scope));
    }
    static ProjectMasterDO child(Long id, String status) {
        var child = new ProjectMasterDO(); child.setId(id); child.setTenantId(7L); child.setLifecycleStatus(status); return child;
    }
    static ProjectTreePathDO path(Long id, int distance) {
        var path = new ProjectTreePathDO(); path.setDescendantProjectId(id); path.setDistance(distance); return path;
    }
}
