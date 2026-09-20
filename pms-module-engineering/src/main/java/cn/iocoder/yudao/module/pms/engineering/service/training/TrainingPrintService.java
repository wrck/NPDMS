package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class TrainingPrintService {
    @Resource private DynamicFormBusinessInstanceApi templateApi;

    /** Called within the training save transaction; only PLT's public contract reads its tables. */
    public void capture(TrainingDO target, Long templateId) {
        if (templateId == null) return; // Legacy callers keep the built-in layout without mutating historical records.
        var fact = templateApi.inspectCurrentRevisionForUsage(new DynamicFormCurrentRevisionQuery(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                TrainingPrintTemplatePolicy.KEY, templateId, TrainingPrintTemplatePolicy.USAGE));
        fact = templateApi.lockAndRevalidateRevisionForUsage(new DynamicFormRevisionRevalidationQuery(
                SecurityFrameworkUtils.getLoginUserId(), fact));
        var conf = JsonUtils.parseTree(fact.formConfJson());
        String snapshot;
        if (conf.has("trainingPrint")) {
            snapshot = JsonUtils.toJsonString(TrainingPrintLayout.parse(conf.path("trainingPrint").toString()));
        } else {
            var rules = JsonUtils.parseTree(fact.formRulesJson());
            var fields = new java.util.HashSet<String>();
            collectFields(rules, fields);
            if (!conf.isObject() || !rules.isArray() || !fields.containsAll(
                    java.util.Set.of("name", "content", "trainingTime", "signatureImageDataUrl"))) {
                throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                        cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_ARGUMENT_INVALID.getCode(),
                        "打印表单须保留培训名称、内容、时间及签字图片字段");
            }
            snapshot = JsonUtils.toJsonString(java.util.Map.of(
                    "engine", "FORM_CREATE_ELEMENT_PLUS", "formConfJson", conf,
                    "formRulesJson", rules));
        }
        target.setPrintTemplateId(templateId);
        target.setPrintRevisionId(fact.templateRevisionId());
        target.setPrintLayoutSnapshot(snapshot);
    }

    private static void collectFields(tools.jackson.databind.JsonNode node, java.util.Set<String> fields) {
        if (node.isObject() && node.path("field").isTextual()) fields.add(node.path("field").asText());
        if (node.isObject() || node.isArray()) for (var child : node) collectFields(child, fields);
    }
}
