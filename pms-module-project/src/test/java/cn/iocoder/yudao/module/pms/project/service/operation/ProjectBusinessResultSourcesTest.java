package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessResultSourcesTest {
    private final BusinessResultSource owner = mock(BusinessResultSource.class);
    private final Type type = new Type("OWNER","ENTITY","COMPLETED");
    private final Query query = new Query(1L,3L,type,"对象:9007199254740993","结果:9007199254740995");
    private ProjectBusinessResultSources registry;
    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(owner.descriptor()).thenReturn(new Descriptor(type,true,true,true));
        registry = new ProjectBusinessResultSources(List.of(owner));
        when(owner.inspect(query)).thenReturn(Observation.available(result(1L,3L,type,query.objectId(),query.resultId())));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void descriptorLookupDoesNotQueryBusinessDataAndRetainsExactStringIds() {
        assertTrue(registry.descriptor(type).historicalLookup()); verify(owner,never()).inspect(any());
        var found = registry.inspect(query).result();
        assertEquals(query.objectId(),found.objectId()); assertEquals(query.resultId(),found.resultId());
    }
    @Test void duplicateOwnerSourcesAreRejectedInsteadOfChoosingOne() {
        assertThrows(IllegalArgumentException.class,()->new ProjectBusinessResultSources(List.of(owner,owner)));
    }
    @Test void unknownTypeAndCrossTenantDoNotCallOwner() {
        assertNull(registry.descriptor(new Type("OTHER","ENTITY","COMPLETED")));
        assertThrows(IllegalArgumentException.class,()->registry.inspect(new Query(2L,3L,type,query.objectId(),query.resultId())));
        assertThrows(IllegalArgumentException.class,()->registry.inspect(new Query(1L,3L,new Type("OTHER","ENTITY","COMPLETED"),"x","y")));
        verify(owner,never()).inspect(any());
    }
    @Test void unsupportedExactLookupIsRejectedBeforeReading() {
        when(owner.descriptor()).thenReturn(new Descriptor(type,true,false,false));
        var currentOnly = new ProjectBusinessResultSources(List.of(owner));
        assertThrows(IllegalArgumentException.class,()->currentOnly.inspect(query));
        verify(owner,never()).inspect(any());
    }
    @ParameterizedTest @ValueSource(strings = {"tenant","project","type","object","result"})
    void doesNotAcceptAnOwnerAnswerForADifferentIdentity(String damage) {
        when(owner.inspect(query)).thenReturn(Observation.available(result(damage.equals("tenant")?2L:1L,damage.equals("project")?4L:3L,
                damage.equals("type")?new Type("OTHER","ENTITY","COMPLETED"):type,
                damage.equals("object")?"another-object":query.objectId(),damage.equals("result")?"another-result":query.resultId())));
        assertThrows(IllegalStateException.class,()->registry.inspect(query));
    }
    @Test void noMatchAndUnavailableAreDistinctAndNotSuccess() {
        for (Status status : List.of(Status.NOT_FOUND,Status.NOT_FORMED,Status.UNAVAILABLE)) {
            when(owner.inspect(query)).thenReturn(Observation.absent(status,"OWNER_REASON"));
            assertEquals(status,registry.inspect(query).status()); assertNull(registry.inspect(query).result());
        }
        assertThrows(IllegalArgumentException.class,()->Observation.absent(Status.AVAILABLE,"OWNER_REASON"));
    }
    @Test void aChangeFeedAloneDoesNotPromiseTransactionalCommitCoverage() {
        var changing = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource.class);
        when(changing.descriptor()).thenReturn(new Descriptor(type,true,true,true));
        var sources = new ProjectBusinessResultSources(List.of(changing));
        assertFalse(sources.commitBarrierSupported(type));
        when(changing.transactionalChangeCoverage()).thenReturn(true);
        assertTrue(sources.commitBarrierSupported(type));
        assertFalse(sources.commitBarrierSupported(new Type("OTHER","ENTITY","COMPLETED")));
        verify(changing,never()).inspect(any());
    }
    private Result result(Long tenant,Long project,Type resultType,String object,String result) {
        return new Result(tenant,project,resultType,object,result,null,null,Validity.CURRENT,null);
    }
}
