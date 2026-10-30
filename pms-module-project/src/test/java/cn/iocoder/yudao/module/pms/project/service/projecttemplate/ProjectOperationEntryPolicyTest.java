package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOperationEntryPolicyTest {
    @Test void rejectsUnknownNullAndConflictingDeclarations() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectOperationEntryPolicy(List.of(provider(Map.of("UNKNOWN", ProjectOperationControlScope.PROJECT_ENTRY_ONLY)))));
        Map<String,ProjectOperationControlScope> absent = new HashMap<>(); absent.put("OWNER.SAVE",null);
        assertThrows(NullPointerException.class, () -> new ProjectOperationEntryPolicy(List.of(provider(absent))));
        var source = provider(Map.of("OWNER.SAVE",ProjectOperationControlScope.PROJECT_ENTRY_ONLY));
        assertThrows(IllegalArgumentException.class, () -> new ProjectOperationEntryPolicy(List.of(source,source)));
    }
    @Test void capturesScopeWithoutExecutingAnOwnerOrKeepingMutableMetadata() {
        Map<String,ProjectOperationControlScope> metadata = new HashMap<>();
        metadata.put("OWNER.SAVE",ProjectOperationControlScope.PROJECT_ENTRY_ONLY);
        var policy = new ProjectOperationEntryPolicy(List.of(provider(metadata)));
        metadata.put("OWNER.SAVE",ProjectOperationControlScope.ALL_ENTRIES);
        assertEquals(ProjectOperationControlScope.PROJECT_ENTRY_ONLY,
                policy.scope(new WriteRequest(1L,"OWNER","ENTITY",null,"OWNER.SAVE",1,"2")));
        assertNull(policy.scope(new WriteRequest(1L,"OWNER","ENTITY",null,"OWNER.SAVE",2,"2")));
        assertNull(policy.scope(null));
    }
    @Test void legacyProviderRemainsUndeclared() {
        var policy = new ProjectOperationEntryPolicy(List.of(provider(Map.of())));
        assertNull(policy.scope(new WriteRequest(1L,"OWNER","ENTITY",null,"OWNER.SAVE",1,"2")));
    }
    private ProjectBusinessOperationProvider provider(Map<String,ProjectOperationControlScope> scopes) {
        return new ProjectBusinessOperationProvider() {
            public List<ProjectBusinessOperationDescriptor> operations() {
                return List.of(new ProjectBusinessOperationDescriptor("OWNER.SAVE",1,"OWNER","ENTITY","保存","SAVE",Set.of(),Object.class,"toString"));
            }
            public Map<String,ProjectOperationControlScope> controlScopes() { return scopes; }
        };
    }
}
