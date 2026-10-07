package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import java.util.List;

/** A declaration is the only model integration; the framework knows none of these names. */
public final class DeclaredNoteDeclaration implements BusinessModelContributor {
    private final DeclaredNoteMapper mapper;
    public DeclaredNoteDeclaration(DeclaredNoteMapper mapper) { this.mapper = mapper; }
    @Override public List<BusinessModelDeclaration> declarations() {
        return List.of(new BusinessModelDeclaration(new BusinessModelDescriptor("IT", "declaredNote",
                "IT_DECLARED_NOTE", 1, BusinessModelKind.AGGREGATE_ROOT, "Declared note", "it:note:query",
                List.of(new BusinessFieldDescriptor("projectRef", "Project", EntityField.Type.NUMBER, true, true, true, null),
                        new BusinessFieldDescriptor("title", "Title", EntityField.Type.TEXT, true, true, true, null),
                        new BusinessFieldDescriptor("amount", "Amount", EntityField.Type.NUMBER, false, true, true, null),
                        new BusinessFieldDescriptor("internalMemo", "Internal memo", EntityField.Type.TEXT, false, false, true, null),
                        new BusinessFieldDescriptor("referenceCode", "Reference", EntityField.Type.TEXT, false, true, false, null),
                        new BusinessFieldDescriptor("tags", "Tags", EntityField.Type.TEXT_LIST, false, true, true, null)),
                List.of(), List.of(new BusinessOperationDescriptor("create", 1, "Create",
                        BusinessOperationDescriptor.StandardOperationKind.CREATE, "it:note:create"),
                        new BusinessOperationDescriptor("save", 1, "Save",
                                BusinessOperationDescriptor.StandardOperationKind.UPDATE, "it:note:update"),
                        new BusinessOperationDescriptor("uppercase", 1, "Uppercase title", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "it:note:uppercase"),
                        new BusinessOperationDescriptor("unsafeMove", 1, "Rejected move", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "it:note:uppercase"),
                        new BusinessOperationDescriptor("unsafeField", 1, "Rejected field", BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, "it:note:uppercase")),
                List.of(), null, new BusinessScopeBinding("project", "projectRef")), DeclaredNoteDO.class, mapper, null));
    }
}
