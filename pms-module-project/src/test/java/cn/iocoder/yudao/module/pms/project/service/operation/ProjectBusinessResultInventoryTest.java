package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessResultInventoryTest {
    private static final Type TYPE=new Type("OWNER","ENTITY","FORMED");
    private final BusinessResultInventorySource source=mock(BusinessResultInventorySource.class);
    private ProjectBusinessResultSources registry;
    private final InventoryQuery query=new InventoryQuery(1L,3L,TYPE,true,List.of("object"),null,2);
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);when(source.descriptor()).thenReturn(new Descriptor(TYPE,true,true,true));registry=new ProjectBusinessResultSources(List.of(source));}
    @AfterEach void after(){TenantContextHolder.clear();}

    @Test void nativeInventoryCursorIsBoundedAndDoesNotDiscardAnUnknownObservation(){
        var first=BusinessResultInventorySource.nativePage(query,List.of("40","41","42"),id->Observation.absent(Status.UNAVAILABLE,"OWNER_REASON"));
        assertEquals("41",first.nextCursor());assertFalse(first.complete());assertEquals(2,first.observations().size());
        var next=new InventoryQuery(1L,3L,TYPE,true,List.of("object"),first.nextCursor(),2);
        var second=BusinessResultInventorySource.nativePage(next,List.of("42"),id->Observation.absent(Status.NOT_FOUND,"DELETED"));
        assertEquals("42",second.nextCursor());assertTrue(second.complete());
        var empty=BusinessResultInventorySource.nativePage(next,List.of(),id->{throw new AssertionError();});
        assertEquals("41",empty.nextCursor());assertTrue(empty.complete());
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","reverse","backward","overflow","oversize"})
    void invalidNativePagesCannotAdvanceTheCursor(String damage){
        var request=new InventoryQuery(1L,3L,TYPE,false,null,"40",2);
        var ids=switch(damage){case "duplicate"->List.of("41","41");case "reverse"->List.of("42","41");case "backward"->List.of("40");case "overflow"->List.of("9223372036854775808");default->List.of("41","42","43","44");};
        assertThrows(RuntimeException.class,()->BusinessResultInventorySource.nativePage(request,ids,id->{throw new AssertionError("must validate before reading");}));
    }
    @ParameterizedTest @ValueSource(strings={"oversize","stalled","empty-more","wrong-object","duplicate"})
    void registryRejectsInvalidProviderPages(String damage){
        var result=Observation.available(new Result(1L,3L,TYPE,damage.equals("wrong-object")?"other":"object","40",null,null,Validity.CURRENT,null));
        var page=switch(damage){
            case "oversize"->new InventoryPage("cursor",true,List.of(result,result,result));
            case "stalled"->new InventoryPage(null,false,List.of(result));
            case "empty-more"->new InventoryPage(null,false,List.of());
            case "duplicate"->new InventoryPage("cursor",true,List.of(result,result));
            default->new InventoryPage("cursor",true,List.of(result));};
        when(source.inventory(query)).thenReturn(page);assertThrows(IllegalStateException.class,()->registry.inventory(query));
    }
    @Test void onlyRealInventoryProvidersAreAvailableAndDescriptorReadsHaveNoBusinessSideEffects(){
        assertTrue(registry.inventorySupported(TYPE));verify(source,never()).inventory(any());
        var old=mock(BusinessResultSource.class);when(old.descriptor()).thenReturn(new Descriptor(TYPE,true,true,true));
        var plain=new ProjectBusinessResultSources(List.of(old));assertFalse(plain.inventorySupported(TYPE));
        assertThrows(IllegalArgumentException.class,()->plain.inventory(query));verify(old,never()).inspect(any());
    }
    @Test void wrongTenantAndUnsupportedHistoryStopBeforeOwnerQueries(){
        assertThrows(IllegalArgumentException.class,()->registry.inventory(new InventoryQuery(2L,3L,TYPE,true,null,null,2)));
        when(source.descriptor()).thenReturn(new Descriptor(TYPE,true,false,false));
        var currentOnly=new ProjectBusinessResultSources(List.of(source));assertThrows(IllegalArgumentException.class,()->currentOnly.inventory(query));
        verify(source,never()).inventory(any());
    }
    @Test void emptyExplicitObjectScopeDoesNotBecomeProjectScope(){
        assertEquals(List.of(),BusinessResultInventorySource.nativeObjects(new InventoryQuery(1L,3L,TYPE,false,List.of(),null,2)));
        assertNull(BusinessResultInventorySource.nativeObjects(new InventoryQuery(1L,3L,TYPE,false,null,null,2)));
        assertThrows(IllegalArgumentException.class,()->BusinessResultInventorySource.nativeObjects(query));
        assertThrows(IllegalArgumentException.class,()->new InventoryQuery(1L,3L,TYPE,false,null,null,101));
    }
}
