package cn.iocoder.yudao.module.system.api.organization;

import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ManagedOrganizationPlanTest {
    private ManagedOrganizationApi.Node department(Long id,String code,Long parentId,String owner) {
        return new ManagedOrganizationApi.Node(null,new DeptDO().setId(id).setCode(code).setName(code)
                .setParentId(parentId).setStatus(0).setSort(1).setVersion(0),owner);
    }
    @Test void newParentAndChildKeepUnassignedIdsUntilApply() {
        var parent=department(null,"P",null,null);var child=department(null,"C",null,null);
        var entries=List.of(new ManagedOrganizationApi.Entry("p",null,parent),new ManagedOrganizationApi.Entry("c","p",child));
        var result=ManagedOrganizationApiImpl.plan(new ManagedOrganizationApi.Command("owner",entries,false,Set.of(),"UPSERT",Set.of()),List.of());
        assertEquals(List.of("CREATED","CREATED"),result.stream().map(ManagedOrganizationApi.Result::action).toList());
        result.forEach(r->assertNull(r.after().id()));assertNull(result.getLast().after().parentId());
        assertNull(parent.id());assertNull(child.id());assertNull(child.parentId());
    }
    @Test void existingAndAssignedParentIdsRemainAvailableToChildPlans() {
        for(boolean assigned:List.of(false,true)) {
            var entries=List.of(new ManagedOrganizationApi.Entry("p",null,department(assigned?21L:null,"P",0L,null)),
                    new ManagedOrganizationApi.Entry("c","p",department(null,"C",null,null)));
            var command=new ManagedOrganizationApi.Command("owner",entries,false,assigned?Set.of("p"):Set.of(),"UPSERT",Set.of());
            var result=ManagedOrganizationApiImpl.plan(command,assigned?List.of():List.of(department(21L,"P",0L,"owner")));
            assertEquals(21L,result.getLast().after().parentId());assertNull(result.getLast().after().id());
        }
    }
    private ManagedOrganizationApi.Node company(Long id,String owner) {
        return new ManagedOrganizationApi.Node(new CompanyDO().setId(id).setCode("001").setName("公司").setStatus(0).setVersion(0),null,owner);
    }
    @Test void doesNotAdoptUnmanagedMatchingCodeWithoutExplicitConsent() {
        var entry=new ManagedOrganizationApi.Entry("c1",null,company(null,null));
        assertEquals("CONFLICT",ManagedOrganizationApiImpl.plan(new ManagedOrganizationApi.Command("owner",List.of(entry),false,Set.of(),"UPSERT",Set.of("c1")),
                List.of(company(11L,null))).getFirst().action());
    }
    @Test void adoptionPreservesTargetIdentity() {
        var entry=new ManagedOrganizationApi.Entry("c1",null,company(null,null));
        var result=ManagedOrganizationApiImpl.plan(new ManagedOrganizationApi.Command("owner",List.of(entry),true,Set.of(),"UPSERT",Set.of("c1")),
                List.of(company(11L,null))).getFirst();
        assertEquals("ADOPTED",result.action());assertEquals(11L,result.after().id());
    }
    @Test void foreignManagedTargetCannotBeAdopted() {
        var entry=new ManagedOrganizationApi.Entry("c1",null,company(null,null));
        assertEquals("CONFLICT",ManagedOrganizationApiImpl.plan(new ManagedOrganizationApi.Command("owner",List.of(entry),true,Set.of(),"UPSERT",Set.of("c1")),
                List.of(company(11L,"other"))).getFirst().action());
    }
    @Test void unchangedSourceDoesNotUpdateBusinessVersion() {
        var entry=new ManagedOrganizationApi.Entry("c1",null,company(11L,null));
        assertEquals("UNCHANGED",ManagedOrganizationApiImpl.plan(new ManagedOrganizationApi.Command("owner",List.of(entry),false,Set.of(),"UPSERT",Set.of("c1")),
                List.of(company(11L,"owner"))).getFirst().action());
    }
    @Test void duplicateAndCyclicTreesAreRejected() {
        var a=new DeptDO().setCode("A").setName("A").setStatus(0).setSort(1).setVersion(0);
        var b=new DeptDO().setCode("B").setName("B").setStatus(0).setSort(2).setVersion(0);
        var entries=List.of(new ManagedOrganizationApi.Entry("a","b",new ManagedOrganizationApi.Node(null,a,null)),
                new ManagedOrganizationApi.Entry("b","a",new ManagedOrganizationApi.Node(null,b,null)));
        assertThrows(IllegalArgumentException.class,()->ManagedOrganizationApiImpl.plan(new ManagedOrganizationApi.Command("owner",entries,false,Set.of(),"UPSERT",Set.of()),List.of()));
    }
    @Test void requestedPrimaryKeyCannotFallBackToMatchingCodeOrTakeForeignOwnership() {
        var entry=new ManagedOrganizationApi.Entry("c1",null,company(21L,null));
        var command=new ManagedOrganizationApi.Command("owner",List.of(entry),true,Set.of("c1"),"UPSERT",Set.of("c1"));
        assertEquals("CONFLICT",ManagedOrganizationApiImpl.plan(command,List.of(company(11L,null))).getFirst().action());
        assertEquals("CONFLICT",ManagedOrganizationApiImpl.plan(command,List.of(company(21L,"other"))).getFirst().action());
        assertEquals("ADOPTED",ManagedOrganizationApiImpl.plan(command,List.of(company(21L,null))).getFirst().action());
    }
    @Test void skipDoesNotBypassOwnershipOrRecreateMissingBindings() {
        var entry=new ManagedOrganizationApi.Entry("c1",null,company(11L,null));
        var command=new ManagedOrganizationApi.Command("owner",List.of(entry),false,Set.of(),"INSERT_IGNORE",Set.of("c1"));
        assertEquals("CONFLICT",ManagedOrganizationApiImpl.plan(command,List.of(company(11L,"other"))).getFirst().action());
        assertEquals("CONFLICT",ManagedOrganizationApiImpl.plan(command,List.of()).getFirst().action());
        assertEquals("SKIPPED",ManagedOrganizationApiImpl.plan(command,List.of(company(11L,"owner"))).getFirst().action());
    }
}
