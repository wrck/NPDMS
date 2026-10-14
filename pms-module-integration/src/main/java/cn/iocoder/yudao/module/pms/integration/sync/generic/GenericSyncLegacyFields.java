package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import java.util.*;

/** Read compatibility for saved tasks predating V378's item_code -> product_code rename.
 * Never changes persisted task JSON, source field names or the public target capability boundary. */
final class GenericSyncLegacyFields {
    private GenericSyncLegacyFields() {}
    static SyncDefinition normalize(SyncDefinition definition) {
        if(definition==null||definition.sources()==null)return definition;
        boolean changed=false;List<SyncDefinition.Source> sources=new ArrayList<>();
        for(var source:definition.sources()) {
            if(source.targets()==null){sources.add(source);continue;}
            boolean sourceChanged=false;List<GenericTargetStep> targets=new ArrayList<>();
            for(var step:source.targets()) {
                if(step.lookups()==null){targets.add(step);continue;}
                boolean stepChanged=false;List<GenericTargetStep.Lookup> lookups=new ArrayList<>();
                for(var lookup:step.lookups()) {
                    if(!"com_sales_order_line".equals(lookup.table())||lookup.match()==null||lookup.outputs()==null
                            || (!lookup.match().containsKey("item_code")&&!lookup.outputs().containsValue("item_code"))) {
                        lookups.add(lookup);continue;
                    }
                    Map<String,String> match=new LinkedHashMap<>();
                    lookup.match().forEach((key,value)->{
                        String canonical=rename(key);
                        if(match.containsKey(canonical)&&!Objects.equals(match.get(canonical),value))
                            throw new IllegalArgumentException("旧物料字段与产品字段关联条件冲突");
                        match.put(canonical,value);
                    });
                    Map<String,String> outputs=new LinkedHashMap<>();
                    lookup.outputs().forEach((key,value)->outputs.put(key,rename(value)));
                    lookups.add(new GenericTargetStep.Lookup(lookup.table(),match,outputs,lookup.onMissing()));stepChanged=true;
                }
                if(stepChanged) {
                    targets.add(new GenericTargetStep(step.name(),step.table(),step.keys(),step.mode(),step.nullPolicy(),
                            step.updateColumns(),step.whenField(),step.newerBy(),step.tieBreaker(),step.mappings(),lookups));
                    sourceChanged=true;
                } else targets.add(step);
            }
            sources.add(sourceChanged?source.toBuilder().targets(targets).build():source);changed|=sourceChanged;
        }
        return changed?definition.toBuilder().sources(sources).build():definition;
    }
    private static String rename(String value){return "item_code".equals(value)?"product_code":value;}
}
