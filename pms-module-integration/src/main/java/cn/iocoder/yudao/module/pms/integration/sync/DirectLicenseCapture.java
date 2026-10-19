package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;
/** No plaintext source row is ever returned to generic snapshots, mapping, evidence or logging. */
@Component @RequiredArgsConstructor
final class DirectLicenseCapture {
    private final DirectLicenseCaptureCipher cipher;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    record Input(SyncDefinition definition,SyncDefinition.Source source,Connection connection) {}
    static boolean reference(SyncDefinition.Source source){return "SECURE_LICENSE".equals(source.readMode());}
    static void validate(SyncDefinition d,SyncDefinition.Source s) {
        
        try {
            if(!DppmsDirectLicenseHistoryTemplate.ADAPTER.equals(d.adapter()) || !"DPPMS".equals(d.sourceSystem()) || !"ONCE".equals(d.mode())
                    || !"SNAPSHOT".equals(d.effectiveReadStrategy()) || !"RESTART_ALL".equals(d.effectiveRestartPolicy())
                    || d.clearBeforeLoad() || d.resetMappingsBeforeLoad() || !"RETAIN".equals(d.missingPolicy()) || !"UPSERT".equals(d.loadingMode())
                    || d.sources()==null || d.sources().size()!=1 || d.maxRows()>200)throw new IllegalArgumentException();
            var facts=approved(s);String project=Objects.toString(s.parameters().get("ownerProjectId"),"");
            if(!project.isBlank())throw new IllegalArgumentException();
            var expected=create(new Request(d.connectionId(),project.isBlank()?null:SyncFieldMapper.primaryKey(project),facts));
            if(!Objects.equals(d.sources(),expected.sources()))throw new IllegalArgumentException();
        }catch(RuntimeException failure){throw new IllegalArgumentException("LICENSE_CONTROLLED_DEFINITION_INVALID");}
    }
    MysqlSyncReader.SourceRows read(Input input) throws SQLException {
        var d=input.definition();var s=input.source();validate(d,s);
        long tenant=TenantContextHolder.getRequiredTenantId();Long actor=d.executionActorId();
        if(actor==null || !permissions.hasAnyPermissions(actor,"pms:integration:execute"))throw new org.springframework.security.access.AccessDeniedException("缺少受控许可读取权限");
        users.validateUser(actor);var facts=approved(s);
        if(facts.isEmpty())throw new IllegalArgumentException("LICENSE_EXPLICIT_APPROVAL_REQUIRED");
        cipher.requireAvailable();
        var expected=new LinkedHashMap<String,Approved>();facts.forEach(f->expected.put(f.id(),f));
        var rows=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();long bytes=0;
        try {
            schema(input.connection());
            try(var statement=input.connection().prepareStatement(DppmsMigrationSql.initial("direct-secure-license-select"))) {
                var keys=facts.stream().map(f->new java.math.BigInteger(f.id())).sorted().toList();
                statement.setBigDecimal(1,new java.math.BigDecimal(keys.getFirst()));
                statement.setBigDecimal(2,new java.math.BigDecimal(keys.getLast()));
                statement.setString(3,Objects.toString(s.parameters().get("approvedLicenseIds"),""));statement.setQueryTimeout(60);
                try(var result=statement.executeQuery()) {
                    while(result.next()) {
                        String id=result.getString("id");var original=expected.get(id);
                        if(original==null || !seen.add(id))throw new IllegalArgumentException("LICENSE_SOURCE_SCOPE_CHANGED");
                        var actual=new Approved(id,result.getString("sn"),result.getString("specModel"),result.getString("contract"),result.getString("contractType"),result.getString("item"),result.getString("status"));
                        if(!Objects.equals(actual,original))throw new IllegalArgumentException("LICENSE_APPROVED_FACTS_CHANGED");
                        char[] code=null;
                        try {
                            String raw=result.getString("licenseCode");code=raw==null?null:raw.toCharArray();raw=null;
                            var envelope=cipher.encrypt(new SyncLicenseCaptureCipher.Capture(tenant,id,code));
                            var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("sn",actual.sn());row.put("specModel",actual.specModel());row.put("contract",actual.contract());
                            row.put("contractType",actual.contractType());row.put("item",actual.item());row.put("status",actual.status());
                            row.put("ciphertext",envelope.ciphertext());row.put("keyId",envelope.keyId());row.put("codeMac",envelope.codeMac());
                            bytes+=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(row).length;
                            if(bytes>d.maxBytes() || rows.size()>=d.maxRows())throw new IllegalArgumentException("LICENSE_SOURCE_SIZE_LIMIT");
                            rows.add(Collections.unmodifiableMap(row));
                        }finally {if(code!=null)Arrays.fill(code,'\0');}
                    }
                }
            }
        }catch(SQLException failure){throw new SQLException("LICENSE_CAPTURE_READ_FAILED");}
        if(!seen.equals(expected.keySet()))throw new IllegalArgumentException("LICENSE_APPROVED_SOURCE_MISSING");
        return new MysqlSyncReader.SourceRows(OBJECT,TABLE,List.copyOf(rows));
    }
    private static void schema(Connection connection) throws SQLException {
        List<String> names=List.of("id","licenseCode","sn","specModel","contract","contractType","item","status");
        List<String> types=List.of("int(11)","varchar(256)","varchar(255)","varchar(256)","varchar(32)","varchar(16)","varchar(255)","varchar(255)");
        int index=0;
        try(var statement=connection.createStatement();var rows=statement.executeQuery(DppmsMigrationSql.initial("secure-license-columns"))) {
            while(rows.next()) {
                if(index>=names.size() || !names.get(index).equals(rows.getString("Field")) || !types.get(index).equals(rows.getString("Type"))
                        || !(index<2?"NO":"YES").equals(rows.getString("Null")) || !(index==0?"PRI":"").equals(rows.getString("Key")))
                    throw new IllegalArgumentException("LICENSE_CAPTURE_SCHEMA_DRIFT");index++;
            }
        }
        if(index!=names.size())throw new IllegalArgumentException("LICENSE_CAPTURE_SCHEMA_DRIFT");
    }
}
