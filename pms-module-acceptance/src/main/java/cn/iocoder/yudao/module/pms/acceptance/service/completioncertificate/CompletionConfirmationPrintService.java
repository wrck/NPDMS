package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CompletionConfirmationPrintService {
    private final DynamicFormBusinessInstanceApi templates;
    public void capture(Long tenant,Long actor,CompletionCertificateDO target,Long templateId,boolean survey){
        if(templateId==null)return;
        String usage=survey?"SATISFACTION_PRINT":"COMPLETION_PRINT";
        var fact=templates.inspectCurrentRevisionForUsage(new DynamicFormCurrentRevisionQuery(tenant,actor,new DynamicFormProviderKey("ACC",usage),templateId,usage));
        fact=templates.lockAndRevalidateRevisionForUsage(new DynamicFormRevisionRevalidationQuery(actor,fact));
        String snapshot=JsonUtils.toJsonString(java.util.Map.of("engine","FORM_CREATE_ELEMENT_PLUS","formConfJson",JsonUtils.parseTree(fact.formConfJson()),"formRulesJson",JsonUtils.parseTree(fact.formRulesJson())));
        if(survey){target.setSatisfactionPrintTemplateId(templateId);target.setSatisfactionPrintRevisionId(fact.templateRevisionId());target.setSatisfactionPrintSnapshot(snapshot);}
        else {target.setCompletionPrintTemplateId(templateId);target.setCompletionPrintRevisionId(fact.templateRevisionId());target.setCompletionPrintSnapshot(snapshot);}
    }
}
