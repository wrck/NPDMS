package cn.iocoder.yudao.module.pms.platform.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static cn.iocoder.yudao.module.pms.platform.businessview.BusinessViewFixtures.*;
import static cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor.ViewSource.*;
import static org.junit.jupiter.api.Assertions.*;

/** PM-03 / F-PLT-003 AC-01, AC-03: source combinations and immutable declarative values. */
class BusinessViewDescriptorTest {

    @Test
    void pageAndFormPreserveAllCommonConfigurationWithoutGrantingPermissions() {
        var page = page();
        var form = form();
        assertEquals(PAGE, page.viewSource());
        assertNull(page.dynamicFormRevisionId());
        assertEquals(DYNAMIC_FORM, form.viewSource());
        assertEquals(91L, form.dynamicFormRevisionId());
        assertEquals(page.tenantId(), form.tenantId());
        assertEquals(page.entityType(), form.entityType());
        assertEquals(page.ownerContext(), form.ownerContext());
        assertEquals(page.viewKey(), form.viewKey());
        assertEquals(page.revisionNo(), form.revisionNo());
        assertEquals(page.contextSchema(), form.contextSchema());
        assertEquals(page.supportedActions(), form.supportedActions());
        assertEquals(page.queryProviderKey(), form.queryProviderKey());
        assertEquals(page.commandProviderKey(), form.commandProviderKey());
        assertEquals(page.permissionProviderKey(), form.permissionProviderKey());
    }

    @Test
    void rejectsMixedSourcesAndMissingOrNonpositiveFormRevision() {
        for (Long revision : List.of(-1L, 0L, 91L)) {
            assertThrows(IllegalArgumentException.class, () -> view(c -> c.formRevision = revision));
        }
        for (Long revision : Arrays.asList(null, -1L, 0L)) {
            assertThrows(IllegalArgumentException.class, () -> view(c -> {
                c.source = DYNAMIC_FORM;
                c.formRevision = revision;
            }));
        }
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.source = null));
        assertThrows(IllegalArgumentException.class, () -> view(c -> {
            c.source = DYNAMIC_FORM;
            c.formRevision = 91L;
            c.componentKey = null;
        }));
    }

    @Test
    void rejectsBlankInvalidKeysPathsUrlsAndScriptInputsAcrossEveryKeyField() {
        List<BiConsumer<Candidate, String>> fields = List.of(
                (c, value) -> c.entity = value, (c, value) -> c.owner = value,
                (c, value) -> c.viewKey = value, (c, value) -> c.componentKey = value,
                (c, value) -> c.query = value, (c, value) -> c.command = value,
                (c, value) -> c.permission = value);
        List<String> invalid = Arrays.asList(null, "", " ", " KEY", "KEY ", "1KEY", "a/b", "../view",
                "C:\\views\\Page", "https://example.test/view", "file:/Page", "java.lang.Runtime()",
                "<script>alert(1)</script>", "SELECT * FROM secret", "a".repeat(129));
        for (BiConsumer<Candidate, String> field : fields) {
            for (String key : invalid) {
                assertThrows(IllegalArgumentException.class, () -> view(c -> field.accept(c, key)), key);
            }
        }
        for (String release : Arrays.asList(null, "", " ", " 1.0", "https://host/v1", "../v1", "a/b",
                "C:\\v1", "javascript:alert(1)", "v".repeat(65))) {
            assertThrows(IllegalArgumentException.class, () -> view(c -> c.componentVersion = release));
        }
        assertDoesNotThrow(() -> view(c -> {
            c.viewKey = "SOL.Requirement:edit_v1-2";
            c.componentVersion = "1.0.0-rc.1+build";
        }));
    }

    @Test
    void respectsPhysicalLengthLimitsAndReservedTenantZero() {
        assertDoesNotThrow(() -> view(c -> {
            c.tenant = 0;
            c.entity = "a".repeat(64);
            c.owner = "a".repeat(32);
            c.viewKey = "a".repeat(128);
            c.componentKey = "a".repeat(128);
            c.query = "a".repeat(128);
            c.command = "a".repeat(128);
            c.permission = "a".repeat(128);
            c.componentVersion = "a".repeat(64);
        }));
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.entity = "a".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.owner = "a".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.tenant = -1));
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.revision = 0));
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.revision = -1));
    }

    @Test
    void requiresObjectSchemaAndNonemptyUniqueStableActionKeys() {
        for (String json : List.of("null", "[]", "true", "1", "\"object\"")) {
            assertThrows(IllegalArgumentException.class, () -> view(c -> c.context = JsonUtils.parseTree(json)));
        }
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.context = null));
        for (String json : List.of("null", "{}", "[]", "[null]", "[1]", "[\"\"]", "[\"VIEW\",\"VIEW\"]",
                "[\"/view\"]", "[\"https://host\"]", "[\"<script>\"]")) {
            assertThrows(IllegalArgumentException.class, () -> view(c -> c.supported = JsonUtils.parseTree(json)));
        }
        assertThrows(IllegalArgumentException.class, () -> view(c -> c.supported = null));
    }

    @Test
    void jsonInputAndOutputMutationCannotChangeConfigurationOrCopies() {
        ObjectNode context = (ObjectNode) schema();
        ArrayNode supported = (ArrayNode) actions("VIEW", "EDIT");
        var view = view(c -> {
            c.context = context;
            c.supported = supported;
        });
        var copy = view.withRevision(2);
        context.put("injected", true);
        supported.add("DELETE");
        ((ObjectNode) view.contextSchema()).put("injectedAgain", true);
        ((ArrayNode) view.supportedActions()).add("ADMIN");
        ((ObjectNode) copy.contextSchema()).put("copyInjection", true);
        assertEquals(schema(), view.contextSchema());
        assertEquals(actions("VIEW", "EDIT"), view.supportedActions());
        assertEquals(schema(), copy.contextSchema());
        assertEquals(view.withRevision(2), copy);
    }
}
