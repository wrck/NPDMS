package cn.iocoder.yudao.module.pms.integration.sync;

import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Editable initial SQL is copied into the versioned task; adapters never force this text at execution. */
final class DppmsMigrationSql {
    private DppmsMigrationSql() {}
    record GroupedSources(String resource, java.util.List<SyncDefinition.Source> sources) {}
    static java.util.List<SyncDefinition.Source> grouped(GroupedSources input) {
        if(input.sources()==null || input.sources().isEmpty()) throw new IllegalArgumentException("合并查询缺少来源对象");
        var result=new java.util.ArrayList<SyncDefinition.Source>();
        String root=input.sources().getFirst().object();
        for(var source:input.sources()) {
            var builder=source.toBuilder().readMode("SQL").table(null).filters(java.util.List.of());
            if(result.isEmpty()) builder.sql(initial(input.resource())).groupKey("_groupKey").rowProjection("_sourceRows");
            else builder.sql(null).queryRef(root).parameters(java.util.Map.of());
            result.add(builder.build());
        }
        return java.util.List.copyOf(result);
    }
    static String initial(String name) {
        return initial(name, new java.util.HashSet<>());
    }
    private static String initial(String name, java.util.Set<String> path) {
        if (!name.matches("[a-z0-9-]+") || !path.add(name))
            throw new IllegalArgumentException("DPPMS初始SQL片段引用无效或循环");
        try (var input = new ClassPathResource("migration/dppms/" + name + ".sql").getInputStream()) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            var matcher = java.util.regex.Pattern.compile("/\\* @include ([a-z0-9-]+) \\*/").matcher(sql);
            String expanded = matcher.replaceAll(m -> java.util.regex.Matcher.quoteReplacement(initial(m.group(1), path)));
            path.remove(name);
            return expanded;
        } catch (IOException error) {
            throw new IllegalStateException("缺少DPPMS整表任务的初始查询配置: " + name, error);
        }
    }
}
