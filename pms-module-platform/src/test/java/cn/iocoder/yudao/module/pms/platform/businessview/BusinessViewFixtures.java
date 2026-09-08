package cn.iocoder.yudao.module.pms.platform.businessview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.ControlledBusinessViewCatalog;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.function.Consumer;

import static cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor.ViewSource.*;
import static cn.iocoder.yudao.module.pms.platform.domain.businessview.ControlledBusinessViewCatalog.ProviderKind.*;

/** PM-03 / F-PLT-003: candidate input and directory facts are intentionally built independently. */
final class BusinessViewFixtures {
    private BusinessViewFixtures() { }

    static JsonNode schema() {
        return JsonUtils.parseTree("""
                {"type":"object","properties":{"projectId":{"type":"integer"}},"required":["projectId"]}
                """);
    }

    static JsonNode actions(String... values) {
        return JsonUtils.parseTree(JsonUtils.toJsonString(List.of(values)));
    }

    static BusinessViewDescriptor page() {
        return view(input -> { });
    }

    static BusinessViewDescriptor form() {
        return view(input -> {
            input.source = DYNAMIC_FORM;
            input.componentKey = "PLT.FormHost";
            input.formRevision = 91L;
        });
    }

    static BusinessViewDescriptor view(Consumer<Candidate> customize) {
        Candidate candidate = new Candidate();
        customize.accept(candidate);
        return candidate.build();
    }

    static final class Candidate {
        long tenant = 1;
        String entity = "SOL.Requirement";
        String owner = "SOL";
        String viewKey = "SOL.Requirement.Edit";
        long revision = 1;
        BusinessViewDescriptor.ViewSource source = PAGE;
        String componentKey = "SOL.RequirementPanel";
        String componentVersion = "1.0.0";
        Long formRevision;
        JsonNode context = schema();
        JsonNode supported = actions("VIEW", "EDIT");
        String query = "SOL.Requirement.Query";
        String command = "SOL.Requirement.Command";
        String permission = "SOL.Requirement.Permission";

        BusinessViewDescriptor build() {
            return new BusinessViewDescriptor(tenant, entity, owner, viewKey, revision, source,
                    componentKey, componentVersion, formRevision, context, supported, query, command, permission);
        }
    }

    static ControlledBusinessViewCatalog.ComponentDescriptor component() {
        return component(1, "SOL", "SOL.Requirement", PAGE, schema(), actions("VIEW", "EDIT", "SUBMIT"),
                "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission");
    }

    static ControlledBusinessViewCatalog.ComponentDescriptor component(
            long tenant, String owner, String entity, BusinessViewDescriptor.ViewSource source,
            JsonNode context, JsonNode allowed, String query, String command, String permission) {
        return new ControlledBusinessViewCatalog.ComponentDescriptor(tenant, "SOL.RequirementPanel", "1.0.0",
                owner, entity, source, context, allowed, query, command, permission);
    }

    static List<ControlledBusinessViewCatalog.ProviderDescriptor> providers() {
        return List.of(
                new ControlledBusinessViewCatalog.ProviderDescriptor(1, "SOL.Requirement.Query", QUERY, "SOL", "SOL.Requirement"),
                new ControlledBusinessViewCatalog.ProviderDescriptor(1, "SOL.Requirement.Command", COMMAND, "SOL", "SOL.Requirement"),
                new ControlledBusinessViewCatalog.ProviderDescriptor(1, "SOL.Requirement.Permission", PERMISSION, "SOL", "SOL.Requirement"));
    }

    static ControlledBusinessViewCatalog catalog() {
        return catalog(component());
    }

    static ControlledBusinessViewCatalog catalog(ControlledBusinessViewCatalog.ComponentDescriptor component) {
        return new ControlledBusinessViewCatalog(List.of(component), providers(), List.of());
    }

    static ControlledBusinessViewCatalog formCatalog(long tenant, boolean available) {
        var host = new ControlledBusinessViewCatalog.ComponentDescriptor(1, "PLT.FormHost", "1.0.0",
                "SOL", "SOL.Requirement", DYNAMIC_FORM, schema(), actions("VIEW", "EDIT"),
                "SOL.Requirement.Query", "SOL.Requirement.Command", "SOL.Requirement.Permission");
        return new ControlledBusinessViewCatalog(List.of(host), providers(),
                List.of(new ControlledBusinessViewCatalog.DynamicFormRevision(tenant, 91, available)));
    }
}
