package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;

class DirectSecureLicenseStreamTest {
    final DirectLicenseCapture capture=mock(DirectLicenseCapture.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final Connection connection=mock(Connection.class);
    final DirectSecureLicenseStream reader=new DirectSecureLicenseStream(capture,permissions,users);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    List<Approved> facts(int n){return java.util.stream.IntStream.rangeClosed(1,n).mapToObj(i->new Approved(""+i,"SN"+i,null,null,null,null,"RAW")).toList();}
    SyncDefinition definition(List<Approved> facts) {
        var d=DppmsDirectLicenseHistoryTemplate.create(7L);
        var s=DppmsDirectLicenseHistoryTemplate.approvedSource(new Request(7L,null,facts));
        return d.toBuilder().sources(List.of(s)).executionActorId(2L).build();
    }
    long read(SyncDefinition d,java.util.function.Consumer<MysqlSyncReader.SourceRows> sink)throws Exception {
        return reader.stream(new DirectSecureLicenseStream.Input(d,d.sources().getFirst(),connection),sink);
    }
    @Test void moreThanPilotLimitStreamsEveryApprovedIdentityInBoundedCommands()throws Exception {
        var facts=facts(401);assertThrows(IllegalArgumentException.class,()->create(new Request(7L,10L,facts)));
        var d=definition(facts);assertDoesNotThrow(()->SyncSecureLicenseReader.validate(d,d.sources().getFirst()));
        var sizes=new ArrayList<Integer>();var capturedIds=new ArrayList<String>();
        when(capture.read(any())).thenAnswer(call->{
            var input=call.getArgument(0,DirectLicenseCapture.Input.class);
            assertSame(connection,input.connection());assertEquals("SNAPSHOT",input.definition().effectiveReadStrategy());
            var selected=approved(input.source());sizes.add(selected.size());
            return new MysqlSyncReader.SourceRows(OBJECT,TABLE,selected.stream().map(f->Map.<String,Object>of("id",f.id(),"ciphertext","synthetic-envelope")).toList());
        });
        assertEquals(401,read(d,rows->rows.rows().forEach(row->capturedIds.add(row.get("id").toString()))));
        assertEquals(List.of(200,200,1),sizes);assertEquals(facts.stream().map(Approved::id).toList(),capturedIds);
    }
    @Test void emptyApprovalDeniedExecuteAndInvalidActorNeverCaptureSecrets()throws Exception {
        var empty=definition(List.of());assertThrows(IllegalArgumentException.class,()->read(empty,rows->fail()));
        var d=definition(facts(1));
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->read(d,rows->fail()));
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        doThrow(new IllegalArgumentException("inactive actor")).when(users).validateUser(2L);
        assertThrows(IllegalArgumentException.class,()->read(d,rows->fail()));
        verifyNoInteractions(capture,connection);
    }
    @Test void pilotProjectCannotBeIntroducedIntoWholeTableDefinition() {
        assertThrows(IllegalArgumentException.class,()->DppmsDirectLicenseHistoryTemplate.approvedSource(new Request(7L,10L,facts(1))));
        var d=definition(facts(1));var parameters=new HashMap<>(d.sources().getFirst().parameters());parameters.put("ownerProjectId","10");
        d.sources().getFirst().parameters(parameters);
        assertThrows(IllegalArgumentException.class,()->DirectSecureLicenseStream.validate(d,d.sources().getFirst()));
    }
    @Test void duplicateAcrossChunksMalformedFactsAndGenericSqlAreRejected() {
        var facts=new ArrayList<>(facts(201));facts.add(facts.getFirst());
        var duplicate=definition(facts);assertThrows(IllegalArgumentException.class,()->DirectSecureLicenseStream.validate(duplicate,duplicate.sources().getFirst()));
        var changed=definition(facts(1));changed.sources().getFirst().sql("SELECT licenseCode FROM pm_project_license_info_from_license");
        assertThrows(IllegalArgumentException.class,()->DirectSecureLicenseStream.validate(changed,changed.sources().getFirst()));
    }
    @Test void missingLaterApprovedRowFailsRatherThanProducingSuccessfulTruncation()throws Exception {
        var d=definition(facts(201));
        when(capture.read(any())).thenReturn(new MysqlSyncReader.SourceRows(OBJECT,TABLE,List.of(Map.of("id","1"))))
                .thenThrow(new IllegalArgumentException("LICENSE_APPROVED_SOURCE_MISSING"));
        var error=assertThrows(IllegalArgumentException.class,()->read(d,rows->{}));assertEquals("LICENSE_APPROVED_SOURCE_MISSING",error.getMessage());
        verify(capture,times(2)).read(any());
    }
}
