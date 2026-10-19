package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;
class SyncSecureLicenseReaderTest {
    PermissionApi permissions;AdminUserApi users;SyncSecureLicenseReader reader;Connection connection;Statement schema;PreparedStatement query;
    ResultSet metadata,result;SyncDefinition definition;Approved approved=new Approved("201","SN","MODEL","CONTRACT","TYPE","ITEM","RAW");
    @BeforeEach void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);permissions=mock(PermissionApi.class);users=mock(AdminUserApi.class);
        reader=new SyncSecureLicenseReader(new SyncLicenseCaptureCipher(SyncLicenseCaptureCipherTest.KEY,"TEST-V1"),permissions,users);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        definition=create(new Request(7L,10L,List.of(approved))).toBuilder().executionActorId(2L).build();
        connection=mock(Connection.class);schema=mock(Statement.class);query=mock(PreparedStatement.class);metadata=mock(ResultSet.class);result=mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(schema);when(schema.executeQuery(anyString())).thenReturn(metadata);
        when(connection.prepareStatement(anyString())).thenReturn(query);when(query.executeQuery()).thenReturn(result);
        var index=new AtomicInteger(-1);when(metadata.next()).thenAnswer(c->index.incrementAndGet()<8);
        var names=List.of("id","licenseCode","sn","specModel","contract","contractType","item","status");
        var types=List.of("int(11)","varchar(256)","varchar(255)","varchar(256)","varchar(32)","varchar(16)","varchar(255)","varchar(255)");
        when(metadata.getString(anyString())).thenAnswer(c->switch(c.<String>getArgument(0)){case "Field"->names.get(index.get());case "Type"->types.get(index.get());case "Null"->index.get()<2?"NO":"YES";case "Key"->index.get()==0?"PRI":"";default->null;});
        when(result.next()).thenReturn(true,false);
        when(result.getString(anyString())).thenAnswer(c->switch(c.<String>getArgument(0)){case "id"->"201";case "licenseCode"->"SYNTHETIC-LICENSE-SECRET";case "sn"->"SN";case "specModel"->"MODEL";case "contract"->"CONTRACT";case "contractType"->"TYPE";case "item"->"ITEM";case "status"->"RAW";default->null;});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    MysqlSyncReader.SourceRows read()throws Exception{return reader.read(new SyncSecureLicenseReader.Input(definition,definition.sources().getFirst(),connection));}
    @Test void onlyCiphertextAndApprovedBusinessFactsReachGenericSource()throws Exception {
        var source=read();assertEquals(1,source.rows().size());var row=source.rows().getFirst();assertEquals(new HashSet<>(COLUMNS),row.keySet());
        String json=JsonUtils.toJsonString(row);assertFalse(json.contains("SYNTHETIC-LICENSE-SECRET"));assertFalse(json.contains("licenseCode"));
        verify(query).setString(1,"201");verify(users).validateUser(2L);assertEquals("TEST-V1",row.get("keyId"));
    }
    @Test void changedFrozenFactsAndMissingApprovedIdsFailWithoutFallback()throws Exception {
        doReturn("CHANGED").when(result).getString("specModel");assertThrows(IllegalArgumentException.class,this::read);verify(result,never()).getString("licenseCode");
    }
    @Test void missingApprovedIdCannotBecomeAnEmptySuccessfulCapture()throws Exception {
        doReturn(false).when(result).next();var error=assertThrows(IllegalArgumentException.class,this::read);
        assertEquals("LICENSE_APPROVED_SOURCE_MISSING",error.getMessage());
    }
    @Test void unauthorizedAndEmptyScopeNeverReadSource()throws Exception {
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,this::read);verifyNoInteractions(connection);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        definition=create(new Request(7L,10L,List.of())).toBuilder().executionActorId(2L).build();
        assertThrows(IllegalArgumentException.class,this::read);verifyNoInteractions(connection);
    }
    @Test void schemaDriftAndDriverErrorsCannotExposeCodes()throws Exception {
        doReturn("wrong").when(metadata).getString("Type");assertThrows(IllegalArgumentException.class,this::read);verify(connection,never()).prepareStatement(anyString());
        reset(metadata);when(schema.executeQuery(anyString())).thenThrow(new SQLException("SYNTHETIC-LICENSE-SECRET"));
        var error=assertThrows(SQLException.class,this::read);assertEquals("LICENSE_CAPTURE_READ_FAILED",error.getMessage());assertNull(error.getCause());
    }
    @Test void controlledDefinitionRejectsPlaintextAndGenericSqlOverrides() {
        var source=definition.sources().getFirst();source.sql("SELECT licenseCode FROM pm_project_license_info_from_license");
        assertThrows(IllegalArgumentException.class,()->SyncSecureLicenseReader.validate(definition,source));
        definition=create(new Request(7L,10L,List.of(approved)));var parameters=new HashMap<>(definition.sources().getFirst().parameters());
        parameters.put("approvedLicenseFacts","[{\"id\":\"201\",\"licenseCode\":\"SYNTHETIC-LICENSE-SECRET\"}]");definition.sources().getFirst().parameters(parameters);
        var error=assertThrows(IllegalArgumentException.class,()->SyncSecureLicenseReader.validate(definition,definition.sources().getFirst()));assertFalse(error.toString().contains("SYNTHETIC-LICENSE-SECRET"));assertNull(error.getCause());
    }
}
