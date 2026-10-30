package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.springframework.stereotype.Component;
@Component
public class SatisfactionPrintTemplatePolicy implements DynamicFormBusinessObjectPolicyProvider {
    @Override public DynamicFormProviderKey providerKey(){return new DynamicFormProviderKey("ACC","SATISFACTION_PRINT");}
    @Override public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query){
        var fields=query.fields().stream().map(DynamicFormFieldDescriptor::fieldKey).collect(java.util.stream.Collectors.toSet());
        boolean allowed="SATISFACTION_PRINT".equals(query.requiredUsage()) && fields.containsAll(java.util.Set.of("name","signatureImageDataUrl"))
                && (fields.contains("answersText") || fields.contains("questionnaireData"));
        return new DynamicFormPolicyFact(query.action(),allowed,allowed?null:"SATISFACTION_PRINT_FIELDS_REQUIRED",query.revisionFactVersion().longValue(),"SATISFACTION_PRINT");
    }
    @Override public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query){return new DynamicFormPolicyFact(query.action(),false,"PRINT_HAS_NO_FORM_INSTANCE",null,"SATISFACTION_PRINT");}
    @Override public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query){return new DynamicFormPolicyFact(query.expectedFact().action(),false,"PRINT_HAS_NO_FORM_INSTANCE",null,"SATISFACTION_PRINT");}
}
