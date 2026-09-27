package cn.iocoder.yudao.module.pms.service.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * B5 批次统一目录声明（P12）：巡检规则进入统一目录。
 * 规则修订/命令/产品类型修订/安全审查为聚合内 4 表版本链，保留本域服务；
 * 六个 srv_*_retired 退役域为数据库只读历史（触发器拒绝写入），不迁移不声明。
 * 服务模块暂无独立 HTTP 入口，统一目录读权限沿用已种子化的 pms:inspection-rule:query。
 */
@Component
public class ServiceBusinessModelContributor implements BusinessModelContributor {

    private final InspectionRuleMapper inspectionRuleMapper;

    public ServiceBusinessModelContributor(InspectionRuleMapper inspectionRuleMapper) {
        this.inspectionRuleMapper = inspectionRuleMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        BusinessModelDescriptor rule = new BusinessModelDescriptor("SRV", "inspectionRule",
                "SRV_INSPECTION_RULE", 1, BusinessModelKind.AGGREGATE_ROOT, "巡检规则",
                "pms:inspection-rule:query",
                List.of(required("detectionId", "探测标识", EntityField.Type.TEXT),
                        required("ruleName", "规则名称", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "srv_inspection_rule");
        return List.of(new BusinessModelDeclaration(rule, InspectionRuleDO.class, inspectionRuleMapper, null));
    }
}
