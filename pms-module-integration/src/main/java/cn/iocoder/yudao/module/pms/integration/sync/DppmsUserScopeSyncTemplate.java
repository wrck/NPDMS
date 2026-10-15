package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Full original scope relations plus all noncredential account fields used to qualify them. */
public final class DppmsUserScopeSyncTemplate {
    private DppmsUserScopeSyncTemplate() {}
    static final List<String> POWER_COLUMNS=List.of("id","fndUserId","username","areapower","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo");
    static Map<String,List<String>> columns() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(var source:DppmsUserSyncTemplate.create(1L).sources())result.put(source.object(),source.mappings().stream().map(SyncDefinition.Mapping::target).toList());
        List<String> roleColumns=new ArrayList<>(DppmsUserRoleSyncTemplate.COLUMNS.get("T_USER_ROLE"));roleColumns.add("migrationCompanyCode");
        result.put("t_user_role",List.copyOf(roleColumns));result.put("fnd_user_power",POWER_COLUMNS);
        return Collections.unmodifiableMap(result);
    }
    public static SyncDefinition create(Long connectionId) {
        List<SyncDefinition.Source> sources=new ArrayList<>(DppmsUserSyncTemplate.create(connectionId).sources().stream().map(s->
                s.toBuilder().readMode("EVIDENCE").sql(null).table(null).queryRef(null).groupKey(null).rowProjection(null)
                        .parameters(Map.of()).evidenceAdapter("DPPMS_USERS").evidenceObject(s.object()).build()).toList());
        sources.add(SyncDefinition.Source.builder().object("t_user_role").sourceObject("t_user_role").readMode("EVIDENCE")
                .evidenceAdapter("DPPMS_USER_ROLES").evidenceObject("T_USER_ROLE")
                .parameters(Map.of()).sourceKey("id").updatedAt(null).columns(List.of()).filters(List.of())
                .mappings(columns().get("t_user_role").stream().map(column->SyncDefinition.Mapping.builder().target(column).source(column).conversion("DIRECT").build()).toList()).build());
        sources.add(SyncDefinition.Source.builder().object("fnd_user_power").sourceObject("fnd_user_power").readMode("SQL")
                .sql("SELECT "+String.join(",",POWER_COLUMNS)+" FROM fnd_user_power").parameters(Map.of()).sourceKey("id").updatedAt(null)
                .columns(List.of()).filters(List.of()).mappings(POWER_COLUMNS.stream().map(column->SyncDefinition.Mapping.builder().target(column).source(column).conversion("DIRECT").build()).toList()).build());
        return SyncDefinition.builder().adapter("DPPMS_USER_SCOPES").sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0)
                .retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000).maxBytes(64L*1024*1024).sources(sources).build();
    }
}
