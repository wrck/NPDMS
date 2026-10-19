package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Four source reads per round; account and relationship rows are shared by their consumers. */
public final class DppmsAccountFoundationSyncTemplate {
    private DppmsAccountFoundationSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        List<SyncDefinition.Source> sources=new ArrayList<>();
        for(String object:List.of("t_user","t_user_info","fnd_user_info","FND_USER_ROLE")){
            List<String> fields=new ArrayList<>("FND_USER_ROLE".equals(object)?DppmsUserRoleSyncTemplate.COLUMNS.get(object):DppmsUserSyncTemplate.COLUMNS.get(object));
            if(!"FND_USER_ROLE".equals(object))fields.addAll("t_user_info".equals(object)?DppmsUserSyncTemplate.EMPLOYMENT_REFERENCES:DppmsUserSyncTemplate.ACCOUNT_REFERENCES);
            var source=source(new SourceInput(object,"FND_USER_ROLE".equals(object)?"fnd_user_info":object,
                    "t_user".equals(object)?"user_id":"id",fields));
            sources.add("t_user".equals(object)?source.toBuilder().sql(DppmsMigrationSql.initial("account-foundation-accounts"))
                    .groupKey("businessKey").rowProjection(DppmsAccountFoundationProjection.ACCOUNTS).build():source.toBuilder().queryRef("t_user").build());
        }
        List<String> roleFields=List.of("sourceRoleId","sourceCode","name","sort","remark","status","creatorName","creatorSourceKey",
                "updaterName","updaterSourceKey","createTime","updateTime");
        sources.add(source(new SourceInput("T_ROLE","t_role","sourceRoleId",roleFields)).toBuilder()
                .sql(DppmsMigrationSql.initial("account-foundation-roles")).groupKey("businessKey").rowProjection(DppmsAccountFoundationProjection.ROLES).build());
        sources.add(source(new SourceInput("FND_ROLE","fnd_roles","sourceRoleId",roleFields)).toBuilder().queryRef("T_ROLE").build());
        sources.add(source(new SourceInput("T_USER_ROLE","t_user_role","id",DppmsUserRoleSyncTemplate.COLUMNS.get("T_USER_ROLE"))).toBuilder()
                .sql(DppmsMigrationSql.initial("account-foundation-relations")).groupKey("_groupKey").rowProjection("_sourceRows").build());
        sources.add(source(new SourceInput("t_user_role","t_user_role","id",DppmsUserScopeSyncTemplate.columns().get("t_user_role"))).toBuilder().queryRef("T_USER_ROLE").build());
        sources.add(source(new SourceInput("fnd_user_power","fnd_user_power","id",DppmsUserScopeSyncTemplate.POWER_COLUMNS)).toBuilder()
                .sql("SELECT "+String.join(",",DppmsUserScopeSyncTemplate.POWER_COLUMNS)+" FROM fnd_user_power").build());
        return SyncDefinition.builder().adapter("DPPMS_ACCOUNT_FOUNDATION").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000).maxBytes(64L*1024*1024)
                .sources(List.copyOf(sources)).build();
    }
    private record SourceInput(String object,String table,String key,List<String> fields) {}
    private static SyncDefinition.Source source(SourceInput input) {
        return SyncDefinition.Source.builder().object(input.object()).sourceObject(input.table()).readMode("SQL")
                .parameters(Map.of()).columns(List.of()).filters(List.of()).sourceKey(input.key()).updatedAt(null)
                .mappings(input.fields().stream().map(field->SyncDefinition.Mapping.builder().target(field).source(field).conversion("DIRECT").build()).toList()).build();
    }
}
