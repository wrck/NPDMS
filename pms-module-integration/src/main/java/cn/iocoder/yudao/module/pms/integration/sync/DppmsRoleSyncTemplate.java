package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reusable role migration/sync definition. Every source role column remains in read-only evidence. */
public final class DppmsRoleSyncTemplate {
    private DppmsRoleSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        return SyncDefinition.builder().adapter("DPPMS_ROLES").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN")
                .cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0)
                .retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(2000).maxBytes(16L * 1024 * 1024)
                .sources(DppmsMigrationSql.grouped(new DppmsMigrationSql.GroupedSources("roles-grouped",List.of(source(false), source(true))))).build();
    }
    private static SyncDefinition.Source source(boolean fnd) {
        String table = fnd ? "fnd_roles" : "t_role";
        String id = fnd ? "id" : "role_id";
        String created = fnd ? "createTime" : "create_time";
        String updated = fnd ? "updateTime" : "update_time";
        String creator = fnd ? "createBy" : "create_by";
        String updater = fnd ? "updateBy" : "update_by";
        var mappings = new ArrayList<SyncDefinition.Mapping>();
        mappings.add(map("sourceRoleId", id));
        mappings.add(fnd ? SyncDefinition.Mapping.builder().target("sourceCode").source("id")
                .conversion("ENUM").values(DppmsRoleCodes.FND_CODES).build() : map("sourceCode", "role_name"));
        mappings.add(map("name", fnd ? "roleName" : "role_name_zn"));
        mappings.add(map("sort", "migrationSort"));
        mappings.add(map("remark", fnd ? "roleRemark" : "remark"));
        mappings.add(SyncDefinition.Mapping.builder().target("status").source("status").conversion("ENUM")
                .values(Map.of("1", 0)).build());
        mappings.add(map("creatorName", creator)); mappings.add(map("creatorSourceKey", "creatorSourceKey"));
        mappings.add(map("updaterName", updater)); mappings.add(map("updaterSourceKey", "updaterSourceKey"));
        mappings.add(map("createTime", created)); mappings.add(map("updateTime", updated));
        return SyncDefinition.Source.builder().object(fnd ? "FND_ROLE" : "T_ROLE").sourceObject(table)
                .readMode("SQL").parameters(Map.of()).columns(List.of()).filters(List.of())
                .sourceKey(id).updatedAt("migrationVersion").mappings(mappings).build();
    }
    private static SyncDefinition.Mapping map(String target, String source) {
        return SyncDefinition.Mapping.builder().target(target).source(source).conversion("DIRECT").build();
    }
}
