package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Original role assignment rows are read in full; account credentials are excluded. */
public final class DppmsUserRoleSyncTemplate {
    private DppmsUserRoleSyncTemplate() {}
    static final Map<String,List<String>> COLUMNS = Map.of("T_USER_ROLE",List.of("id","user_id","role_id","comp_id","username"),
            "FND_USER_ROLE",DppmsUserSyncTemplate.COLUMNS.get("fnd_user_info"));
    public static SyncDefinition create(Long connectionId) {
        return SyncDefinition.builder().adapter("DPPMS_USER_ROLES").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000).maxBytes(64L*1024*1024)
                .sources(List.of(source(false),source(true))).build();
    }
    private static SyncDefinition.Source source(boolean fnd) {
        String object=fnd?"FND_USER_ROLE":"T_USER_ROLE";
        return SyncDefinition.Source.builder().object(object).sourceObject(fnd?"fnd_user_info":"t_user_role")
                .readMode(fnd?"EVIDENCE":"SQL").sql(fnd?null:DppmsMigrationSql.initial("user-roles-grouped"))
                .evidenceAdapter(fnd?"DPPMS_USERS":null).evidenceObject(fnd?"fnd_user_info":null)
                .groupKey(fnd?null:"_groupKey").rowProjection(fnd?null:"_sourceRows")
                .parameters(Map.of()).columns(List.of()).filters(List.of()).sourceKey("id").updatedAt(null)
                .mappings(COLUMNS.get(object).stream().map(column->SyncDefinition.Mapping.builder().target(column).source(column).conversion("DIRECT").build()).toList()).build();
    }
}
