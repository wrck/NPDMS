package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
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
            templates=List.copyOf(configured);
        }catch(IOException e){throw new IllegalStateException("通用同步模板读取失败",e);}
    }
    public List<Template> list(Long connectionId) {
        return templates.stream().map(t->new Template(t.key(),t.name(),t.definition().toBuilder().connectionId(connectionId).build())).toList();
    }
}
