package cn.iocoder.yudao.module.pms.platform.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.ControlledBusinessViewCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.businessview.BusinessViewFixtures.*;
import static cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor.ViewSource.*;
import static cn.iocoder.yudao.module.pms.platform.domain.businessview.ControlledBusinessViewCatalog.ProviderKind.*;
import static org.junit.jupiter.api.Assertions.*;

/** PM-03 / F-PLT-003 AC-01, AC-03: independently supplied directory contracts, not runtime authorization. */
class ControlledBusinessViewCatalogTest {

    @Test
    void acceptsActionSubsetAndStructurallyEqualSchemaWithoutProviderExecution() {
        assertDoesNotThrow(() -> catalog().validate(page()));
        assertDoesNotThrow(() -> catalog().validate(view(c -> {
            c.supported = actions("EDIT", "VIEW");
            c.context = JsonUtils.parseTree("""
                    {"required":["projectId"],"properties":{"projectId":{"type":"integer"}},"type":"object"}
                    """);
        })));
        assertDoesNotThrow(() -> formCatalog(1, true).validate(form()));
        // A syntactically valid class-like key is still only a key; it is never reflected or loaded.
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.componentKey = "java.lang.Runtime")));
    }

    @Test
    void rejectsMissingNullOrDuplicateDirectoryFacts() {
        var component = component();
        var provider = providers().getFirst();
        var form = new ControlledBusinessViewCatalog.DynamicFormRevision(1, 91, true);
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(null, providers(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), providers(), null));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(Arrays.asList(component, null), providers(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), Arrays.asList(provider, null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), providers(), Arrays.asList(form, null)));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component, component), providers(), List.of()));
        var differentPayload = component(1, "ACC", "SOL.Requirement", PAGE, schema(), actions("VIEW"),
                "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission");
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component, differentPayload), providers(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), List.of(provider, provider), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), providers(), List.of(form, form)));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(), providers(), List.of()).validate(page()));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(List.of(component), List.of(), List.of()).validate(page()));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(null));
    }

    @Test
    void rejectsUnknownComponentExactVersionAndCrossTenantAccess() {
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.componentKey = "SOL.Unknown")));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.componentVersion = "2.0.0")));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.tenant = 2)));
        assertThrows(IllegalArgumentException.class, () -> catalog(component(2, "SOL", "SOL.Requirement", PAGE,
                schema(), actions("VIEW", "EDIT"), "SOL.Requirement.Query", "SOL.Requirement.Command",
                "SOL.Requirement.Permission")).validate(page()));
    }

    @Test
    void refusesOwnerEntitySourceActionAndSchemaMismatchAgainstIndependentDirectory() {
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.owner = "ACC")));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.entity = "ACC.Report")));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> {
            c.source = DYNAMIC_FORM;
            c.formRevision = 91L;
        })));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.supported = actions("DELETE"))));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.context = JsonUtils.parseTree("{}"))));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.context = JsonUtils.parseTree("""
                {"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}
                """))));
        assertThrows(IllegalArgumentException.class, () -> catalog(component(1, "SOL", "SOL.Requirement", PAGE,
                schema(), actions("VIEW"), "SOL.Requirement.Query", "SOL.Requirement.Command",
                "SOL.Requirement.Permission")).validate(page()));
    }

    @Test
    void refusesProviderKeyRoleOwnerEntityAndTenantMismatches() {
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.query = "SOL.OtherQuery")));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.command = "SOL.OtherCommand")));
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(view(c -> c.permission = "SOL.OtherPermission")));
        for (int index = 0; index < providers().size(); index++) {
            var original = providers().get(index);
            var wrongFacts = List.of(
                    new ControlledBusinessViewCatalog.ProviderDescriptor(2, original.providerKey(), original.kind(), "SOL", "SOL.Requirement"),
                    new ControlledBusinessViewCatalog.ProviderDescriptor(1, original.providerKey(), original.kind(), "ACC", "SOL.Requirement"),
                    new ControlledBusinessViewCatalog.ProviderDescriptor(1, original.providerKey(), original.kind(), "SOL", "ACC.Report"),
                    new ControlledBusinessViewCatalog.ProviderDescriptor(1, original.providerKey(), original.kind() == QUERY ? COMMAND : QUERY, "SOL", "SOL.Requirement"));
            for (var wrong : wrongFacts) {
                var facts = new ArrayList<>(providers());
                facts.set(index, wrong);
                assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(
                        List.of(component()), facts, List.of()).validate(page()));
            }
            var missing = new ArrayList<>(providers());
            missing.remove(index);
            assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog(
                    List.of(component()), missing, List.of()).validate(page()));
        }
    }

    @Test
    void positiveFormIdAloneDoesNotProveAvailableSameTenantRevisionOrMatchingHost() {
        assertThrows(IllegalArgumentException.class, () -> catalog().validate(form()));
        assertThrows(IllegalArgumentException.class, () -> formCatalog(2, true).validate(form()));
        assertThrows(IllegalArgumentException.class, () -> formCatalog(1, false).validate(form()));
        assertThrows(IllegalArgumentException.class, () -> formCatalog(1, true).validate(view(c -> {
            c.source = DYNAMIC_FORM;
            c.componentKey = "PLT.FormHost";
            c.formRevision = 92L;
        })));
        assertThrows(IllegalArgumentException.class, () -> formCatalog(1, true).validate(view(c -> c.componentKey = "PLT.FormHost")));
    }

    @Test
    void directoryUsesDefensiveCopiesOfCollectionsAndJsonNodes() {
        ObjectNode context = (ObjectNode) schema();
        ArrayNode allowed = (ArrayNode) actions("VIEW", "EDIT");
        var component = component(1, "SOL", "SOL.Requirement", PAGE, context, allowed,
                "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission");
        var components = new ArrayList<>(List.of(component));
        var providers = new ArrayList<>(providers());
        var forms = new ArrayList<ControlledBusinessViewCatalog.DynamicFormRevision>();
        var catalog = new ControlledBusinessViewCatalog(components, providers, forms);
        components.clear();
        providers.clear();
        forms.add(new ControlledBusinessViewCatalog.DynamicFormRevision(1, 91, true));
        context.put("injected", true);
        allowed.add("DELETE");
        ((ObjectNode) component.contextSchema()).put("injectedAgain", true);
        ((ArrayNode) component.allowedActions()).add("ADMIN");
        assertDoesNotThrow(() -> catalog.validate(page()));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(view(c -> c.supported = actions("DELETE"))));
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(view(c -> c.supported = actions("ADMIN"))));
    }

    @Test
    void directoryDescriptorsEnforceTheirOwnShapesAndPhysicalLimits() {
        assertThrows(IllegalArgumentException.class, () -> component(1, "o".repeat(33), "SOL.Requirement", PAGE,
                schema(), actions("VIEW"), "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission"));
        assertThrows(IllegalArgumentException.class, () -> component(1, "SOL", "e".repeat(65), PAGE,
                schema(), actions("VIEW"), "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission"));
        assertThrows(IllegalArgumentException.class, () -> component(1, "SOL", "SOL.Requirement", PAGE,
                JsonUtils.parseTree("[]"), actions("VIEW"), "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission"));
        assertThrows(IllegalArgumentException.class, () -> component(1, "SOL", "SOL.Requirement", PAGE,
                schema(), actions(), "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission"));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog.ProviderDescriptor(1, "https://host", QUERY, "SOL", "SOL.Requirement"));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog.DynamicFormRevision(1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ControlledBusinessViewCatalog.DynamicFormRevision(-1, 91, true));
    }
}
