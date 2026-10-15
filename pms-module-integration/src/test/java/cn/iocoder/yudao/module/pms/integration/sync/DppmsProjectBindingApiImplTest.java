package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsProjectBindingApiImplTest {
    final SyncBindingMapper mapper=mock(SyncBindingMapper.class);
    final DppmsProjectBindingApiImpl api=new DppmsProjectBindingApiImpl(mapper);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    private SyncBindingDO binding(Long id,String code) {
        var original=new LinkedHashMap<String,Object>();DppmsProjectMasterSyncTemplate.ORIGINAL_COLUMNS.forEach(name->original.put(name,tools.jackson.databind.node.NullNode.getInstance()));
        original.put("projectId","12");original.put("projectType","10");original.put("projectCode","P-0");
        return new SyncBindingDO().setSourceKey("12").setTargetId(id).setFieldsJson(JsonUtils.toJsonString(Map.of("originalFields",original,
                "project",Map.of("id",id,"tenantId",1,"projectType","10","projectCode",code))));
    }
    @Test void exactReferenceRetainsOriginalAndAssignedCodesAndDeduplicatesConsistentBindings() {
        when(mapper.selectIdentitySources(argThat(q->"DPPMS_PROJECT_MASTER".equals(q.adapter()) && "pm_project_header".equals(q.sourceObject())))).thenReturn(List.of(binding(100L,"P-1"),binding(100L,"P-1")));
        assertEquals(new ProjectBinding(18L,"12",100L,"10","P-0","P-1"),api.resolve(new Query(1L,18L,"12")));
        verify(mapper).selectIdentitySources(argThat(query->query.tenantId()==1 && query.connectionId()==18
                && "DPPMS_PROJECT_MASTER".equals(query.adapter()) && "pm_project_header".equals(query.sourceObject()) && query.sourceKeys().equals(List.of("12"))));
    }
    @Test void emptyScopeIsEmptyAndInvalidTenantOrConnectionCannotQuery() {
        assertEquals(List.of(),api.resolve(new BatchQuery(1L,18L,List.of())));
        assertThrows(IllegalArgumentException.class,()->api.resolve(new Query(2L,18L,"12")));
        assertThrows(IllegalArgumentException.class,()->api.resolve(new Query(1L,0L,"12")));
        verifyNoInteractions(mapper);
    }
    @Test void ambiguousAndIncompleteBindingsBlockRatherThanSelectingFirst() {
        when(mapper.selectIdentitySources(any())).thenReturn(List.of(binding(100L,"P-1"),binding(101L,"P-1")));
        assertThrows(IllegalArgumentException.class,()->api.resolve(new Query(1L,18L,"12")));
        var incomplete=binding(100L,"P-1");incomplete.setFieldsJson("{}");
        when(mapper.selectIdentitySources(any())).thenReturn(List.of(incomplete));
        assertThrows(IllegalArgumentException.class,()->api.resolve(new Query(1L,18L,"12")));
    }
    @Test void originalAliasRequiresAnExactCanonicalMasterAndRejectsConflictingOldBindings() {
        var alias=new SyncBindingDO().setSourceKey("11").setTargetId(100L).setFieldsJson(JsonUtils.toJsonString(Map.of(
                "originalIdentity",Map.of("originalProjectId","11","canonicalProjectId","12","projectType","10","projectCode","P-0"),
                "project",Map.of("id",100L,"tenantId",1L,"projectType","10","projectCode","P-1"))));
        when(mapper.selectIdentitySources(any())).thenAnswer(call->{cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries.IdentitySources q=call.getArgument(0);
            return "DPPMS_PROJECT_IDENTITY".equals(q.adapter())?List.of(alias):q.sourceKeys().contains("12")?List.of(binding(100L,"P-1")):List.of();});
        assertEquals(new ProjectBinding(18L,"11",100L,"10","P-0","P-1"),api.resolve(new Query(1L,18L,"11")));
        alias.setTargetId(101L);assertThrows(IllegalArgumentException.class,()->api.resolve(new Query(1L,18L,"11")));
        alias.setTargetId(100L);
        doReturn(List.of()).when(mapper).selectIdentitySources(argThat(q->q!=null && "DPPMS_PROJECT_MASTER".equals(q.adapter())));
        assertThrows(IllegalArgumentException.class,()->api.resolve(new Query(1L,18L,"11")));
    }
}
