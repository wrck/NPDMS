package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import java.util.List;

/**
 * 统一模型描述：由一份业务声明生成，同时服务业务页面、模板配置和运行时查询。
 * stableCode 是稳定业务编码，不随类名改变；权限只缩小可见集合，不形成另一套定义。
 */
public record BusinessModelDescriptor(
        String ownerModule,
        String entityType,
        String stableCode,
        int contractVersion,
        BusinessModelKind kind,
        String title,
        String authorizationPolicyRef,
        List<BusinessFieldDescriptor> fields,
        List<BusinessRelationDescriptor> relations,
        List<BusinessOperationDescriptor> operations,
        List<BusinessCapabilityBinding> capabilities,
        String viewCode,
        BusinessScopeBinding scopeBinding) {

    /** Legacy declarations retain their explicit Owner policy; new defaults require a scope declaration. */
    public BusinessModelDescriptor(String ownerModule, String entityType, String stableCode, int contractVersion,
            BusinessModelKind kind, String title, String authorizationPolicyRef,
            List<BusinessFieldDescriptor> fields, List<BusinessRelationDescriptor> relations,
            List<BusinessOperationDescriptor> operations, List<BusinessCapabilityBinding> capabilities, String viewCode) {
        this(ownerModule, entityType, stableCode, contractVersion, kind, title, authorizationPolicyRef,
                fields, relations, operations, capabilities, viewCode, null);
    }
}
