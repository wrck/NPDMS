package cn.iocoder.yudao.module.pms.platform.support.model;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.ArrayList;
import java.util.List;
/** One explicit binding produces the complete ordinary model; optional operations describe only differences. */
public final class DefaultBusinessModels {
    private DefaultBusinessModels() { }
    private static boolean queryable(BusinessModelIntrospector.IntrospectedField field) {
        var mapping=field.property().getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        return BusinessFieldDescriptor.scalar(field.type()) && (mapping==null || mapping.exist());
    }
    public static BusinessModelDeclaration project(String owner, String type, String stableCode, String title,
            String permissionPrefix, Class<? extends BaseProjectBusinessEntity> entity, BaseMapper<?> mapper,
            List<BusinessOperationDescriptor> additionalOperations) {
        return project(owner,type,stableCode,title,permissionPrefix,entity,mapper,additionalOperations,false);
    }
    public static BusinessModelDeclaration project(String owner, String type, String stableCode, String title,
            String permissionPrefix, Class<? extends BaseProjectBusinessEntity> entity, BaseMapper<?> mapper,
            List<BusinessOperationDescriptor> additionalOperations, boolean aggregateFields) {
        if (permissionPrefix == null || permissionPrefix.isBlank()) throw new IllegalArgumentException("Business permission prefix is required");
        var fields = (aggregateFields ? BusinessModelIntrospector.aggregateFields(entity) : BusinessModelIntrospector.businessFields(entity)).stream()
                // Only deliberately exposed fields enter the public model, never incidental database columns.
                .filter(field -> field.property().isAnnotationPresent(BusinessModelField.class)).map(field -> {
            var annotation = field.property().getAnnotation(BusinessModelField.class);
            return new BusinessFieldDescriptor(field.code(), annotation.name().isBlank() ? field.code() : annotation.name(),
                    field.type(), field.required(), annotation.readable(), annotation.writable(), annotation.dictionaryRef().isBlank() ? null : annotation.dictionaryRef(),
                    annotation.displayOrder(),annotation.readable() && annotation.listVisible(),
                    annotation.readable() && annotation.searchable() && queryable(field),
                    annotation.readable() && annotation.sortable() && queryable(field));
        }).toList();
        var operations = new ArrayList<>(List.of(
                new BusinessOperationDescriptor("create", 1, "新建", BusinessOperationDescriptor.StandardOperationKind.CREATE, permissionPrefix + ":create"),
                new BusinessOperationDescriptor("save", 1, "保存", BusinessOperationDescriptor.StandardOperationKind.UPDATE, permissionPrefix + ":update"),
                new BusinessOperationDescriptor("delete", 1, "删除", BusinessOperationDescriptor.StandardOperationKind.DELETE, permissionPrefix + ":delete")));
        operations.addAll(additionalOperations == null ? List.of() : additionalOperations);
        return new BusinessModelDeclaration(new BusinessModelDescriptor(owner, type, stableCode, 1, BusinessModelKind.AGGREGATE_ROOT,
                title, permissionPrefix + ":query", fields, List.of(), List.copyOf(operations), List.of(), null,
                new BusinessScopeBinding("project", "projectId")), entity, mapper, null);
    }
}
