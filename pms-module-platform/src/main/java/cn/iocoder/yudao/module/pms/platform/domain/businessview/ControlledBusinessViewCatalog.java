package cn.iocoder.yudao.module.pms.platform.domain.businessview;

import tools.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewRules.*;

/**
 * PM-03 / F-PLT-003: immutable facts supplied by the caller's controlled component/Provider
 * directory. This class does not discover classes, load URLs/scripts, contact Providers,
 * authenticate the caller or prove that a Provider is online. Never build this directory
 * from a registration request: configuration and directory have deliberately distinct types.
 */
public final class ControlledBusinessViewCatalog {

    public enum ProviderKind { QUERY, COMMAND, PERMISSION }

    /** One independently declared component contract, not a BusinessViewDescriptor wrapper. */
    public record ComponentDescriptor(
            long tenantId, String componentKey, String componentVersion,
            String ownerContext, String entityType, BusinessViewDescriptor.ViewSource viewSource,
            JsonNode contextSchema, JsonNode allowedActions,
            String queryProviderKey, String commandProviderKey, String permissionProviderKey) {

        public ComponentDescriptor {
            require(tenantId >= 0, "component.tenantId: must be nonnegative");
            componentKey = key(componentKey, "component.componentKey");
            componentVersion = BusinessViewRules.componentVersion(componentVersion);
            ownerContext = key(ownerContext, "component.ownerContext");
            entityType = key(entityType, "component.entityType");
            require(viewSource != null, "component.viewSource: required");
            contextSchema = schema(contextSchema);
            allowedActions = actions(allowedActions, "component.allowedActions");
            queryProviderKey = key(queryProviderKey, "component.queryProviderKey");
            commandProviderKey = key(commandProviderKey, "component.commandProviderKey");
            permissionProviderKey = key(permissionProviderKey, "component.permissionProviderKey");
        }

        @Override
        public JsonNode contextSchema() {
            return contextSchema.deepCopy();
        }

        @Override
        public JsonNode allowedActions() {
            return allowedActions.deepCopy();
        }
    }

    /** A declared Provider identity/role; presence is not a runtime health or permission grant. */
    public record ProviderDescriptor(long tenantId, String providerKey, ProviderKind kind,
                                     String ownerContext, String entityType) {
        public ProviderDescriptor {
            require(tenantId >= 0, "provider.tenantId: must be nonnegative");
            providerKey = key(providerKey, "provider.providerKey");
            require(kind != null, "provider.kind: required");
            ownerContext = key(ownerContext, "provider.ownerContext");
            entityType = key(entityType, "provider.entityType");
        }
    }

    /** Exact form revision availability observed by the caller, not a database lookup. */
    public record DynamicFormRevision(long tenantId, long revisionId, boolean availableForNewReference) {
        public DynamicFormRevision {
            require(tenantId >= 0, "form.tenantId: must be nonnegative");
            require(revisionId > 0, "form.revisionId: must be positive");
        }
    }

    private record ComponentId(long tenantId, String key, String version) { }
    private record ProviderId(long tenantId, String key, ProviderKind kind) { }
    private record FormId(long tenantId, long revisionId) { }

    private final Map<ComponentId, ComponentDescriptor> components;
    private final Map<ProviderId, ProviderDescriptor> providers;
    private final Map<FormId, DynamicFormRevision> forms;

    public ControlledBusinessViewCatalog(Collection<ComponentDescriptor> components,
                                         Collection<ProviderDescriptor> providers,
                                         Collection<DynamicFormRevision> forms) {
        require(components != null, "component directory: required");
        require(providers != null, "Provider directory: required");
        require(forms != null, "form revision directory: required (empty for PAGE)");
        Map<ComponentId, ComponentDescriptor> componentIndex = new HashMap<>();
        for (ComponentDescriptor component : components) {
            require(component != null, "component directory: null entry");
            ComponentId id = new ComponentId(component.tenantId(), component.componentKey(), component.componentVersion());
            require(componentIndex.putIfAbsent(id, component) == null, "component directory: duplicate exact component");
        }
        Map<ProviderId, ProviderDescriptor> providerIndex = new HashMap<>();
        for (ProviderDescriptor provider : providers) {
            require(provider != null, "Provider directory: null entry");
            ProviderId id = new ProviderId(provider.tenantId(), provider.providerKey(), provider.kind());
            require(providerIndex.putIfAbsent(id, provider) == null, "Provider directory: duplicate Provider key/role");
        }
        Map<FormId, DynamicFormRevision> formIndex = new HashMap<>();
        for (DynamicFormRevision form : forms) {
            require(form != null, "form revision directory: null entry");
            require(formIndex.putIfAbsent(new FormId(form.tenantId(), form.revisionId()), form) == null,
                    "form revision directory: duplicate exact revision");
        }
        this.components = Map.copyOf(componentIndex);
        this.providers = Map.copyOf(providerIndex);
        this.forms = Map.copyOf(formIndex);
    }

    /** Checks supplied facts only; actions still require Owner authorization at every execution. */
    public void validate(BusinessViewDescriptor view) {
        require(view != null, "view: required");
        ComponentDescriptor component = components.get(new ComponentId(
                view.tenantId(), view.componentKey(), view.componentVersion()));
        require(component != null, "component: unknown exact version in tenant directory");
        require(component.ownerContext().equals(view.ownerContext()), "component: Owner mismatch");
        require(component.entityType().equals(view.entityType()), "component: entityType mismatch");
        require(component.viewSource() == view.viewSource(), "component: viewSource mismatch");
        require(actionKeys(component.allowedActions()).containsAll(actionKeys(view.supportedActions())),
                "component: supportedActions not allowed");
        // Exact JSON structure is the supported compatibility proof. Do not guess subtyping,
        // resolve remote $ref or add a general JSON Schema execution framework here.
        require(component.contextSchema().equals(view.contextSchema()), "component: contextSchema incompatible");
        require(component.queryProviderKey().equals(view.queryProviderKey()), "component: queryProviderKey mismatch");
        require(component.commandProviderKey().equals(view.commandProviderKey()), "component: commandProviderKey mismatch");
        require(component.permissionProviderKey().equals(view.permissionProviderKey()),
                "component: permissionProviderKey mismatch");
        validateProvider(view, view.queryProviderKey(), ProviderKind.QUERY);
        validateProvider(view, view.commandProviderKey(), ProviderKind.COMMAND);
        validateProvider(view, view.permissionProviderKey(), ProviderKind.PERMISSION);
        if (view.viewSource() == BusinessViewDescriptor.ViewSource.DYNAMIC_FORM) {
            DynamicFormRevision form = forms.get(new FormId(view.tenantId(), view.dynamicFormRevisionId()));
            require(form != null && form.availableForNewReference(),
                    "dynamicFormRevisionId: unavailable exact revision in tenant directory");
        }
    }

    private void validateProvider(BusinessViewDescriptor view, String key, ProviderKind kind) {
        ProviderDescriptor provider = providers.get(new ProviderId(view.tenantId(), key, kind));
        require(provider != null, kind + " Provider: unknown key/role in tenant directory");
        require(provider.ownerContext().equals(view.ownerContext()), kind + " Provider: Owner mismatch");
        require(provider.entityType().equals(view.entityType()), kind + " Provider: entityType mismatch");
    }
}
