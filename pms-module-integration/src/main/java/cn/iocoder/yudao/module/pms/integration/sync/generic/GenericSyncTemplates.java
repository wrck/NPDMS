package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class GenericSyncTemplates {
    public record Template(String key,String name,SyncDefinition definition) {}
    private final List<Template> templates;
    public GenericSyncTemplates() {
        try(var in=new ClassPathResource("sync/generic-templates.json").getInputStream()) {
            var configured=new ArrayList<>(List.of(JsonUtils.parseObject(in.readAllBytes(),Template[].class)));
            Map<String,Template> byKey=new HashMap<>();configured.forEach(t->byKey.put(t.key(),t));
            var sources=new ArrayList<SyncDefinition.Source>();
            for(String key:List.of("contract-master","shipment-contract","shipment-package","device-sn"))
                sources.addAll(byKey.get(key).definition().sources());
            try(var sql=new ClassPathResource("sync/dppms-shipment-contract.sql").getInputStream()) {
                String checkedSource=new String(sql.readAllBytes(),StandardCharsets.UTF_8);
                sources.replaceAll(source->"SHIPMENT_CONTRACT".equals(source.object())
                        ?source.toBuilder().sql(checkedSource).sourceKey("sync_evidence_key").issueField("sync_source_issue").build():source);
            }
            configured.add(new Template("delivery-full-sync","DPPMS 合同、装箱单与发货记录完整同步",
                    byKey.get("contract-master").definition().toBuilder().taskKey("delivery-full-sync").sources(sources).build()));
            templates=configured.stream().map(GenericSyncTemplates::withCompanyConversion).toList();
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
                        mappings,step.lookups(),step.sameVersionColumns()));
            }
            sources.add(source.toBuilder().targets(targets).build());
        }
        return new Template(template.key(),template.name(),definition.toBuilder().sources(sources).build());
    }
    public List<Template> list(Long connectionId) {
        return templates.stream().map(t->new Template(t.key(),t.name(),t.definition().toBuilder().connectionId(connectionId).build())).toList();
    }
}
