package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Reviewed task definitions; expanded SQL is frozen in the saved task version. */
public final class DppmsConsolidatedTemplate {
    private DppmsConsolidatedTemplate() {}
    public record Request(String taskKey,Long connectionId) {}
    public static SyncDefinition create(Request request) {
        if(request==null || !Set.of("P01","P08","P14","P15","P21","P23").contains(request.taskKey())
                || request.connectionId()==null || request.connectionId()<=0)throw new IllegalArgumentException("合并任务或来源连接无效");
        String resource="P15".equals(request.taskKey())?"P15-local-owner":request.taskKey();
        try(var input=new ClassPathResource("migration/dppms/tasks/"+resource+".json").getInputStream()) {
            var definition=JsonUtils.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8),SyncDefinition.class);
            definition.connectionId(request.connectionId());
            for(var source:definition.sources()) if(source.sql()!=null && source.sql().startsWith("@dppms:"))
                source.sql(DppmsMigrationSql.initial(source.sql().substring(7)));
            if("P01".equals(request.taskKey())){
                var sources=new java.util.ArrayList<>(definition.sources());
                for(var source:sources){
                    var mappings=new java.util.ArrayList<>(source.mappings());
                    for(String field:DppmsOrganizationProjection.METADATA)mappings.add(SyncDefinition.Mapping.builder()
                            .source(field).target(field).conversion("DIRECT").build());
                    source.mappings(java.util.List.copyOf(mappings));
                }
                sources.getFirst().sql(DppmsMigrationSql.initial("organization-canonical"));
                sources.getFirst().groupKey("_groupKey").rowProjection(DppmsOrganizationProjection.PROJECTION);
                sources.add(SyncDefinition.Source.builder().object("COMPANY_REFERENCE").sourceObject("t_company").readMode("SQL")
                        .sql("SELECT id AS sourceCompanyId,compCode AS sourceCompanyCode FROM t_company").sourceKey("sourceCompanyId")
                        .parameters(java.util.Map.of()).columns(java.util.List.of()).filters(java.util.List.of())
                        .mappings(java.util.List.of("sourceCompanyId","sourceCompanyCode").stream().map(field->SyncDefinition.Mapping.builder()
                                .source(field).target(field).conversion("DIRECT").build()).toList()).build());
                definition.sources(java.util.List.copyOf(sources));
            }
            return definition;
        }catch(java.io.IOException error){throw new IllegalStateException("合并任务初始配置缺失",error);}
    }
}
