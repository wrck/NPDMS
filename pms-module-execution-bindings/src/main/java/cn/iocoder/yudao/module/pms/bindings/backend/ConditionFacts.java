package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

import java.util.LinkedHashMap;
import java.util.Map;

/** 条件事实收集：按定义声明的条件字段逐项观察；事实读取经统一访问端口（系统观察）。 */
final class ConditionFacts {

    private ConditionFacts() {
    }

    static Map<String, FactObservation> collect(BusinessFactPort factPort, ProcessDefinitionSnapshot snapshot,
                                                EntityRef ref, String sceneCode) {
        Map<String, FactObservation> facts = new LinkedHashMap<>();
        for (var condition : snapshot.conditions()) {
            facts.put(condition.fieldCode(),
                    factPort.observeField(EntityDataRef.current(ref), condition.fieldCode(), sceneCode));
        }
        return facts;
    }
}
