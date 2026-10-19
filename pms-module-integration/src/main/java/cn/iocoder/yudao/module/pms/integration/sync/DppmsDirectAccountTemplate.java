package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Raw source primary keys; the complete snapshot is reconciled using the approved account rules. */
public final class DppmsDirectAccountTemplate {
    public static final String ADAPTER="DPPMS_ACCOUNT_FOUNDATION_DIRECT";
    private DppmsDirectAccountTemplate() {}

    public static SyncDefinition create(Long connectionId) {
        var original=DppmsAccountFoundationSyncTemplate.create(connectionId);
        Map<String,SyncDefinition.Source> roleSources=new HashMap<>();
        DppmsRoleSyncTemplate.create(connectionId).sources().forEach(s->roleSources.put(s.object(),s));
        List<SyncDefinition.Source> sources=new ArrayList<>();
        for(var source:original.sources()) {
            var builder=source.toBuilder().queryRef(null).groupKey(null).rowProjection(null).updatedAt(null);
            switch(source.object()) {
                case "t_user","t_user_info","fnd_user_info" -> builder
                        .sql(DppmsMigrationSql.initial("direct-account-"+source.object().replace('_','-')))
                        .groupKey("sourceId").rowProjection("_sourceRows");
                case "FND_USER_ROLE" -> builder.sql(null).queryRef("fnd_user_info");
                case "T_ROLE","FND_ROLE" -> builder=roleSources.get(source.object()).toBuilder()
                        .sql(DppmsMigrationSql.initial("T_ROLE".equals(source.object())?"direct-account-t-role":"direct-account-fnd-roles"))
                        .queryRef(null).groupKey(null).rowProjection(null).updatedAt(null);
                case "T_USER_ROLE" -> builder.sql(DppmsMigrationSql.initial("account-foundation-relations"))
                        .groupKey("_groupKey").rowProjection("_sourceRows");
                case "t_user_role" -> builder.sql(null).queryRef("T_USER_ROLE");
                case "fnd_user_power" -> { /* Existing raw primary-key source. */ }
                default -> throw new IllegalStateException("Unknown approved P04 object");
            }
            if("t_user_info".equals(source.object())) {
                // Preserve the adjudicated 391 -> migrated parent 88 mapping when a task is recreated.
                // Unknown source departments still require an explicit mapping; raw evidence is unchanged.
                Map<String,Object> departments=new LinkedHashMap<>();
                for(int id:new int[]{0,1,4,9,85,138,181,206,256,358,359,360,363,392,393,595,596,599,613,614,615})
                    departments.put(Integer.toString(id),id);
                departments.put("391",88);
                builder.mappings(source.mappings().stream().map(mapping->"depID".equals(mapping.target())
                        ?mapping.toBuilder().conversion("ENUM").values(Map.copyOf(departments)).build():mapping).toList());
            }
            sources.add(builder.build());
        }
        return original.toBuilder().adapter(ADAPTER).sources(List.copyOf(sources)).build();
    }
}
