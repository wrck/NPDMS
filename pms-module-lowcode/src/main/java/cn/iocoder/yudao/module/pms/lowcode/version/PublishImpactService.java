package cn.iocoder.yudao.module.pms.lowcode.version;

import cn.iocoder.yudao.module.pms.lowcode.dto.PublishImpactDTO;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeConnector;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeEntity;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeForm;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeList;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeMicroflow;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeRule;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeConnectorService;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeEntityService;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeFormService;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeListService;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeMicroflowService;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布影响范围分析服务（批次5-T5，借鉴 OutSystems LifeTime 影响分析）。
 *
 * <p>反向查询：给定源配置（如 ENTITY），找出所有引用它的下游配置。
 * 通过扫描下游配置表的快照/定义字段，匹配源配置的 code。</p>
 *
 * <p>严重度（severity）分级：
 * <ul>
 *   <li>HIGH: ENTITY 变更影响 FORM/LIST（结构性强）</li>
 *   <li>MEDIUM: CONNECTOR/RULE 变更影响 MICROFLOW（运行时依赖）</li>
 *   <li>LOW: MICROFLOW 变更影响 TRIGGER（可延迟生效）</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishImpactService {

    private final LowCodeEntityService entityService;
    private final LowCodeFormService formService;
    private final LowCodeListService listService;
    private final LowCodeMicroflowService microflowService;
    private final LowCodeConnectorService connectorService;
    private final LowCodeRuleService ruleService;

    /**
     * 分析指定配置发布/回滚的影响范围。
     *
     * @param configType   源配置类型
     * @param configId     源配置 ID
     * @param configCode   源配置编码（用于反向匹配）
     * @return 影响范围 DTO
     */
    public PublishImpactDTO analyzeImpact(String configType, Long configId, String configCode) {
        List<PublishImpactDTO.ImpactItem> items = new ArrayList<>();
        if (configCode == null || configCode.isBlank()) {
            return PublishImpactDTO.builder()
                    .sourceConfigType(configType)
                    .sourceConfigId(configId)
                    .sourceConfigCode(configCode)
                    .impactedConfigs(items)
                    .totalImpacted(0)
                    .build();
        }

        switch (configType) {
            case "ENTITY" -> {
                // ENTITY 影响 FORM（formConfig 引用 entityCode）+ LIST（listConfig 引用 entityCode）
                findFormsReferencingEntity(configCode, items);
                findListsReferencingEntity(configCode, items);
            }
            case "CONNECTOR" -> {
                // CONNECTOR 影响 MICROFLOW（definition 节点 config 引用 connectorCode）
                findMicroflowsReferencing("connectorCode", configCode, "CONNECTOR", items, "MEDIUM");
            }
            case "RULE" -> {
                // RULE 影响 MICROFLOW（definition 节点 config 引用 ruleCode）
                findMicroflowsReferencing("ruleCode", configCode, "RULE", items, "MEDIUM");
            }
            case "MICROFLOW" -> {
                // MICROFLOW 影响其他 MICROFLOW（CALL_MICROFLOW 节点引用 microflowCode）
                findMicroflowsReferencing("microflowCode", configCode, "MICROFLOW", items, "LOW");
            }
            default -> {
                // 其他类型暂不做反向影响分析
                log.debug("配置类型 {} 暂不支持影响范围分析", configType);
            }
        }

        return PublishImpactDTO.builder()
                .sourceConfigType(configType)
                .sourceConfigId(configId)
                .sourceConfigCode(configCode)
                .impactedConfigs(items)
                .totalImpacted(items.size())
                .build();
    }

    private void findFormsReferencingEntity(String entityCode, List<PublishImpactDTO.ImpactItem> items) {
        List<LowCodeForm> forms = formService.list();
        for (LowCodeForm form : forms) {
            if (form.getFormConfig() != null && form.getFormConfig().contains("\"" + entityCode + "\"")) {
                items.add(PublishImpactDTO.ImpactItem.builder()
                        .configType("FORM")
                        .configId(form.getId())
                        .configCode(form.getCode())
                        .referenceField("entityCode")
                        .status(form.getStatus())
                        .severity("HIGH")
                        .build());
            }
        }
    }

    private void findListsReferencingEntity(String entityCode, List<PublishImpactDTO.ImpactItem> items) {
        List<LowCodeList> lists = listService.list();
        for (LowCodeList list : lists) {
            if (list.getListConfig() != null && list.getListConfig().contains("\"" + entityCode + "\"")) {
                items.add(PublishImpactDTO.ImpactItem.builder()
                        .configType("LIST")
                        .configId(list.getId())
                        .configCode(list.getCode())
                        .referenceField("entityCode")
                        .status(list.getStatus())
                        .severity("HIGH")
                        .build());
            }
        }
    }

    private void findMicroflowsReferencing(String referenceField, String targetCode,
                                             String dependencyLabel,
                                             List<PublishImpactDTO.ImpactItem> items,
                                             String severity) {
        List<LowCodeMicroflow> microflows = microflowService.list();
        String pattern = "\"" + referenceField + "\":\"" + targetCode + "\"";
        for (LowCodeMicroflow mf : microflows) {
            if (mf.getDefinition() != null && mf.getDefinition().contains(pattern)) {
                items.add(PublishImpactDTO.ImpactItem.builder()
                        .configType("MICROFLOW")
                        .configId(mf.getId())
                        .configCode(mf.getCode())
                        .referenceField(referenceField)
                        .status(mf.getStatus())
                        .severity(severity)
                        .build());
            }
        }
    }
}
