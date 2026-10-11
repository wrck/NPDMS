package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.springframework.stereotype.Component;
@Component
public class CompletionPrintTemplatePolicy implements DynamicFormBusinessObjectPolicyProvider {
    @Override public DynamicFormProviderKey providerKey(){return new DynamicFormProviderKey("ACC","COMPLETION_PRINT");}
    @Override public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query){
        var fields=query.fields().stream().map(DynamicFormFieldDescriptor::fieldKey).collect(java.util.stream.Collectors.toSet());
        boolean allowed="COMPLETION_PRINT".equals(query.requiredUsage()) && fields.containsAll(java.util.Set.of("name","completionDate","signatureImageDataUrl"));
        return new DynamicFormPolicyFact(query.action(),allowed,allowed?null:"COMPLETION_PRINT_FIELDS_REQUIRED",query.revisionFactVersion().longValue(),"COMPLETION_PRINT");
    }
    @Override public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query){return new DynamicFormPolicyFact(query.action(),false,"PRINT_HAS_NO_FORM_INSTANCE",null,"COMPLETION_PRINT");}
    @Override public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query){return new DynamicFormPolicyFact(query.expectedFact().action(),false,"PRINT_HAS_NO_FORM_INSTANCE",null,"COMPLETION_PRINT");}
}
