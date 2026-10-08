package cn.iocoder.yudao.module.pms.platform.support.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class DefaultProjectBusinessModelTest {
    private static <M> M mapper(Class<M> type) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> { throw new UnsupportedOperationException("Metadata test must not execute SQL"); }));
    }

    @ProjectBusinessModel(ownerModule = "IT", entityType = "note", stableCode = "IT_NOTE",
            name = "记录", permissionPrefix = "it:note")
    static class Note extends BaseProjectBusinessEntity {
        @NotBlank @BusinessModelField(name = "标题") private String title;
        @BusinessModelField(writable = false) private String generatedCode;
        private String internalSecret;
    }
    @ProjectBusinessModel(ownerModule = "IT", entityType = "other", stableCode = "IT_OTHER",
            name = "另一记录", permissionPrefix = "it:other")
    static class Other extends BaseProjectBusinessEntity {
        @BusinessModelField private String description;
    }
    interface NoteMapper extends BaseMapper<Note> { }
    interface OtherMapper extends BaseMapper<Other> { }

    @Test void thinBindingGetsInheritedProjectScopeFieldsAndIndependentPermissions() {
        var declaration = new DefaultProjectBusinessModel<>(Note.class, mapper(NoteMapper.class)).declarations().getFirst();
        var model = declaration.descriptor();
        assertEquals("IT_NOTE", model.stableCode());
        assertEquals(new BusinessScopeBinding("project", "projectId"), model.scopeBinding());
        assertEquals("it:note:query", model.authorizationPolicyRef());
        assertEquals(List.of("projectId", "title", "generatedCode"), model.fields().stream().map(BusinessFieldDescriptor::code).toList());
        assertTrue(model.fields().getFirst().required());
        assertTrue(model.fields().get(1).required());
        assertFalse(model.fields().get(2).writable());
        assertTrue(model.fields().stream().noneMatch(field -> field.code().equals("internalSecret") || field.code().equals("tenantId")));
        assertEquals(List.of("it:note:create", "it:note:update", "it:note:delete"), model.operations().stream().map(BusinessOperationDescriptor::authorizationPolicyRef).toList());
    }

    @Test void secondEntityUsesSameDefaultsWithoutAnyOwnerAdapter() {
        var model = new DefaultProjectBusinessModel<>(Other.class, mapper(OtherMapper.class)).declarations().getFirst().descriptor();
        assertEquals("IT_OTHER", model.stableCode());
        assertEquals(List.of("projectId", "description"), model.fields().stream().map(BusinessFieldDescriptor::code).toList());
        assertEquals("it:other:create", model.operations().getFirst().authorizationPolicyRef());
    }

    @Test void extensionIsEvaluatedAfterSubclassInitializationAndRetainsDefaults() {
        class Extension extends DefaultProjectBusinessModel<Note> {
            private final String action = "publish";
            Extension() { super(Note.class, mapper(NoteMapper.class)); }
            @Override protected List<BusinessOperationDescriptor> additionalOperations(String prefix) {
                var operations = new ArrayList<>(super.additionalOperations(prefix));
                operations.add(new BusinessOperationDescriptor(action, 1, "特殊操作",
                        BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND, prefix + ":" + action));
                return List.copyOf(operations);
            }
        }
        var binding = new Extension();
        assertEquals("publish", binding.declarations().getFirst().descriptor().operations().getLast().code());
        assertSame(binding.declarations().getFirst(), binding.declarations().getFirst());
    }

    @Test void unrelatedSubclassMustDeclareItsOwnIdentity() {
        class Undeclared extends Note { }
        assertThrows(BusinessContractException.class, () -> new DefaultProjectBusinessModel(Undeclared.class, mapper(BaseMapper.class)));
    }
}
