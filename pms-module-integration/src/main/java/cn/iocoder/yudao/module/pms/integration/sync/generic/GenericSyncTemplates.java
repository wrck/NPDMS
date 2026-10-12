package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class GenericSyncTemplates {
    public record Template(String key,String name,SyncDefinition definition) {}
    private final List<Template> templates;
    public GenericSyncTemplates() {
        try(var in=new ClassPathResource("sync/generic-templates.json").getInputStream()) {
            templates=Arrays.stream(JsonUtils.parseObject(in.readAllBytes(),Template[].class))
                    .map(GenericSyncTemplates::withCompanyConversion).toList();
        }catch(IOException e){throw new IllegalStateException("通用同步模板读取失败",e);}
    }
    private static Template withCompanyConversion(Template template) {
        var definition = template.definition();
        if (!"DPPMS".equals(definition.sourceSystem())) return template;
        List<SyncDefinition.Source> sources = new ArrayList<>();
        for (var source : definition.sources()) {
            if (source.targets() == null) { sources.add(source); continue; }
            List<GenericTargetStep> targets = new ArrayList<>();
            for (var step : source.targets()) {
                List<SyncDefinition.Mapping> mappings = new ArrayList<>();
                for (var mapping : step.mappings()) {
                    mappings.add("company_code".equals(mapping.target()) && "company_code".equals(mapping.source())
                            ? mapping.toBuilder().conversion(DppmsCompanyCodeConverter.CONVERSION).build()
                            : mapping);
                }
                targets.add(new GenericTargetStep(step.name(),step.table(),step.keys(),step.mode(),
                        step.nullPolicy(),step.updateColumns(),step.whenField(),step.newerBy(),step.tieBreaker(),
                        mappings,step.lookups()));
            }
            sources.add(source.toBuilder().targets(targets).build());
        }
        return new Template(template.key(),template.name(),definition.toBuilder().sources(sources).build());
    }
    public List<Template> list(Long connectionId) {
        return templates.stream().map(t->new Template(t.key(),t.name(),t.definition().toBuilder().connectionId(connectionId).build())).toList();
    }
}
