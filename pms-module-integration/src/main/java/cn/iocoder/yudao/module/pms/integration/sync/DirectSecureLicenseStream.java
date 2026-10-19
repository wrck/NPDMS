package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDirectLicenseHistoryTemplate.*;

/** New P37 only: approved non-secret manifest, one consistent source snapshot, bounded encrypted chunks. */
@Component @RequiredArgsConstructor
final class DirectSecureLicenseStream {
    private final DirectLicenseCapture capture;
    private final PermissionApi permissions;
    private final AdminUserApi users;
    record Input(SyncDefinition definition,SyncDefinition.Source source,Connection connection) {}
    static boolean requested(SyncDefinition d) {
        return ADAPTER.equals(d.adapter()) && "STREAMING_CURSOR".equals(d.effectiveReadStrategy());
    }
    static void validate(SyncDefinition d,SyncDefinition.Source s) {
        try {
            if(!requested(d) || !"P37_DIRECT".equals(d.taskKey()) || !"DPPMS".equals(d.sourceSystem())
                    || !"ONCE".equals(d.mode()) || !"RESTART_ALL".equals(d.effectiveRestartPolicy())
                    || d.autoPaging() || d.clearBeforeLoad() || d.resetMappingsBeforeLoad()
                    || !"RETAIN".equals(d.missingPolicy()) || !"UPSERT".equals(d.loadingMode())
                    || d.sources()==null || d.sources().size()!=1 || d.effectiveChunkSize()>200)
                throw new IllegalArgumentException();
            var manifest=DirectLicenseApprovalManifest.load(s);String raw=Objects.toString(s.parameters().get("ownerProjectId"),"");
            if(!raw.isBlank())throw new IllegalArgumentException();
            var prototype=DppmsSecureLicenseSyncTemplate.create(new DppmsSecureLicenseSyncTemplate.Request(d.connectionId(),null,List.of())).sources().getFirst();
            var expected=prototype.toBuilder().parameters(Map.of("approvedLicenseIds",manifest.ids(),
                    "approvedLicenseFacts",s.parameters().get("approvedLicenseFacts"),"ownerProjectId",raw)).build();
            if(!Objects.equals(s,expected))throw new IllegalArgumentException();
        }catch(RuntimeException failure){throw new IllegalArgumentException("LICENSE_CONTROLLED_DEFINITION_INVALID");}
    }
    long stream(Input input,Consumer<MysqlSyncReader.SourceRows> consumer) throws SQLException {
        var d=input.definition();var source=input.source();validate(d,source);
        TenantContextHolder.getRequiredTenantId();Long actor=d.executionActorId();
        if(actor==null || !permissions.hasAnyPermissions(actor,"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少受控许可读取权限");
        users.validateUser(actor);var facts=approved(source);
        if(facts.isEmpty())throw new IllegalArgumentException("LICENSE_EXPLICIT_APPROVAL_REQUIRED");
        int size=d.effectiveChunkSize();long total=0;
        for(int offset=0;offset<facts.size();offset+=size) {
            var selected=facts.subList(offset,Math.min(offset+size,facts.size()));
            var bounded=DppmsSecureLicenseSyncTemplate.create(new DppmsSecureLicenseSyncTemplate.Request(d.connectionId(),null,selected))
                    .toBuilder().adapter(ADAPTER).executionActorId(actor).maxBytes(d.maxBytes()).build();
            var rows=capture.read(new DirectLicenseCapture.Input(bounded,bounded.sources().getFirst(),input.connection()));
            consumer.accept(rows);total+=rows.rows().size();
        }
        return total;
    }
}
