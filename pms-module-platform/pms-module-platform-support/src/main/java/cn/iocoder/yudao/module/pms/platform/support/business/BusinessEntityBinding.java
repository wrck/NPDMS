package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.*;
import java.util.*;

/** Metadata belongs to this typed service/Mapper binding. It does not require registration in a global catalog. */
final class BusinessEntityBinding<E extends BaseProjectBusinessEntity> {
    final Class<E> type;
    final BusinessModelDeclaration mapping;
    final List<BusinessModelIntrospector.IntrospectedField> fields;
    BusinessEntityBinding(Class<E> type, BusinessMapper<E> mapper, List<BusinessOperationDescriptor> operations, java.util.function.UnaryOperator<List<BusinessOperationDescriptor>> configureOperations) {
        this.type = type;
        var annotation = type.getDeclaredAnnotation(ProjectBusinessModel.class);
        if (annotation == null) throw new BusinessContractException("BUSINESS_IDENTITY_REQUIRED", "Business entity must define its own stable identity");
        var initial = DefaultBusinessModels.project(annotation.ownerModule(), annotation.entityType(), annotation.stableCode(),
                annotation.name(), annotation.permissionPrefix(), type, mapper, operations, true);
        var descriptor=initial.descriptor();
        var configured=new BusinessModelDescriptor(descriptor.ownerModule(),descriptor.entityType(),descriptor.stableCode(),descriptor.contractVersion(),
                descriptor.kind(),descriptor.title(),descriptor.authorizationPolicyRef(),descriptor.fields(),descriptor.relations(),
                List.copyOf(configureOperations.apply(descriptor.operations())),descriptor.capabilities(),descriptor.viewCode(),descriptor.scopeBinding());
        mapping=new BusinessModelDeclaration(configured,type,mapper,null,annotation.nativeEntityType().isBlank()?null:annotation.nativeEntityType());
        for (String identity : List.of(annotation.ownerModule(), annotation.entityType(), annotation.stableCode()))
            if (!identity.matches("[A-Za-z][A-Za-z0-9_-]{0,127}"))
                throw new BusinessContractException("BUSINESS_IDENTITY_INVALID", "Business identity must be a stable code");
        var operationCodes = new HashSet<String>();
        for (var operation : mapping.descriptor().operations())
            if (!operationCodes.add(operation.code()) || operation.version() < 1 || operation.authorizationPolicyRef() == null || operation.authorizationPolicyRef().isBlank())
                throw new BusinessContractException("BUSINESS_OPERATION_INVALID", "Business operations require unique codes, versions and permissions");
        fields = BusinessModelIntrospector.aggregateFields(type);
    }
    E create(Map<String,Object> values) {
        try {
            E result = type.getDeclaredConstructor().newInstance();
            patch(result, values);
            return result;
        } catch (ReflectiveOperationException failure) { throw new BusinessContractException("ENTITY_NOT_CREATABLE", "Entity requires an accessible no-argument constructor"); }
    }
    void patch(E target, Map<String,Object> values) {
        if (values == null) throw new BusinessContractException("INPUT_REQUIRED", "Business values are required");
        for (var entry : values.entrySet()) {
            var field = fields.stream().filter(item -> item.code().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new BusinessContractException("FIELD_NOT_OPEN", "Unknown business field: " + entry.getKey()));
            var exposure = field.property().getAnnotation(BusinessModelField.class);
            if (exposure == null || !exposure.writable()) throw new BusinessContractException("FIELD_NOT_WRITABLE", "Business field is not writable: " + entry.getKey());
            try { field.property().set(target, DeclaredBusinessFieldValues.convert(entry.getValue(), field)); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
    }
    Map<String,Object> values(E entity) { return BusinessModelIntrospector.readValues(entity, fields); }
    Map<String,Object> readable(E entity) {
        var all = values(entity); var result = new LinkedHashMap<String,Object>();
        mapping.descriptor().fields().stream().filter(BusinessFieldDescriptor::readable).forEach(field -> result.put(field.code(), all.get(field.code())));
        return Collections.unmodifiableMap(result);
    }
}
