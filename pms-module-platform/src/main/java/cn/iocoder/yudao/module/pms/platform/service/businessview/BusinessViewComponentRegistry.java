package cn.iocoder.yudao.module.pms.platform.service.businessview;

import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.*;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.BusinessViewDescriptor;
import cn.iocoder.yudao.module.pms.platform.domain.businessview.ControlledBusinessViewCatalog;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewErrors.UNAVAILABLE;

/** PM-03: only deployed Owner beans supply the directory. Missing/conflicting entries are excluded. */
@Component
public class BusinessViewComponentRegistry {
    private record Key(String componentKey, String componentVersion) { }
    private record Entry(BusinessViewComponentProvider provider, BusinessViewComponentProvider.Component component) { }
    private final Map<Key, Entry> entries;

    public BusinessViewComponentRegistry(List<BusinessViewComponentProvider> providers) {
        List<Entry> candidates = new ArrayList<>();
        for (BusinessViewComponentProvider provider : providers) {
            try {
                var component = provider.component();
                declaration(0L, component); // independently validate code-owned declarations
                candidates.add(new Entry(provider, component));
            } catch (RuntimeException invalidDeclaration) {
                // Invalid deployed definitions are not selectable; requests fail as unavailable.
            }
        }
        Map<Key, Long> componentCounts = candidates.stream().collect(Collectors.groupingBy(
                entry -> key(entry.component()), Collectors.counting()));
        Map<String, Set<String>> providerOwners = new HashMap<>();
        for (Entry entry : candidates) {
            var component = entry.component();
            for (String id : providerIdentities(component)) {
                providerOwners.computeIfAbsent(id, ignored -> new HashSet<>())
                        .add(component.ownerContext() + "/" + component.entityType());
            }
        }
        Map<Key, Entry> valid = new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparing(entry -> entry.component().componentKey()
                + ":" + entry.component().componentVersion())).forEach(entry -> {
            if (componentCounts.get(key(entry.component())) == 1
                    && providerIdentities(entry.component()).stream().allMatch(id -> providerOwners.get(id).size() == 1)) {
                valid.put(key(entry.component()), entry);
            }
        });
        entries = Collections.unmodifiableMap(valid);
    }

    public List<BusinessViewComponentProvider.Component> components(Context context) {
        return entries.values().stream().filter(entry -> entry.provider().canConfigure(context, ConfigurationAction.QUERY))
                .map(Entry::component).toList(); // empty means no usable components; never allow all
    }

    public Set<String> readableOwners(Context context) {
        return components(context).stream().map(BusinessViewComponentProvider.Component::ownerContext)
                .collect(Collectors.toUnmodifiableSet());
    }

    public BusinessViewComponentProvider.Component requireComponent(String key, String version) {
        return requireEntry(key, version).component();
    }

    public void requireConfiguration(Context context, String key, String version, ConfigurationAction action) {
        if (!requireEntry(key, version).provider().canConfigure(context, action)) throw exception(FORBIDDEN);
    }

    public boolean canConfigureOwner(Context context, String owner, ConfigurationAction action) {
        return entries.values().stream().filter(entry -> entry.component().ownerContext().equals(owner))
                .anyMatch(entry -> entry.provider().canConfigure(context, action));
    }

    public void requireOwner(Context context, String owner, ConfigurationAction action) {
        if (!canConfigureOwner(context, owner, action)) throw exception(FORBIDDEN);
    }

    public void validate(Context context, BusinessViewDescriptor descriptor, ValidationMode mode) {
        Entry selected = requireEntry(descriptor.componentKey(), descriptor.componentVersion());
        Dependencies dependencies = selected.provider().validateConfiguration(context, descriptor.dynamicFormRevisionId(), mode);
        if (dependencies == null) throw exception(UNAVAILABLE);
        var components = entries.values().stream().map(entry -> declaration(context.tenantId(), entry.component())).toList();
        Set<ControlledBusinessViewCatalog.ProviderDescriptor> providers = new LinkedHashSet<>();
        for (Entry entry : entries.values()) {
            var component = entry.component();
            providers.add(provider(context, component, component.queryProviderKey(), ControlledBusinessViewCatalog.ProviderKind.QUERY));
            providers.add(provider(context, component, component.commandProviderKey(), ControlledBusinessViewCatalog.ProviderKind.COMMAND));
            providers.add(provider(context, component, component.permissionProviderKey(), ControlledBusinessViewCatalog.ProviderKind.PERMISSION));
        }
        var forms = descriptor.dynamicFormRevisionId() == null
                ? List.<ControlledBusinessViewCatalog.DynamicFormRevision>of()
                : List.of(new ControlledBusinessViewCatalog.DynamicFormRevision(context.tenantId(),
                descriptor.dynamicFormRevisionId(), dependencies.dynamicFormAvailable()));
        new ControlledBusinessViewCatalog(components, providers, forms).validate(descriptor);
    }

    /** Only the deployed PLATFORM form adapter currently implements the concrete batch lock protocol. */
    public void lockDependencies(Context context, List<BusinessViewDescriptor> descriptors) {
        PlatformDynamicFormBusinessViewProvider formProvider = null;
        List<PlatformDynamicFormBusinessViewProvider.Dependency> dependencies = new ArrayList<>();
        for (BusinessViewDescriptor descriptor : descriptors) {
            Entry entry = requireEntry(descriptor.componentKey(), descriptor.componentVersion());
            if (descriptor.viewSource() != BusinessViewDescriptor.ViewSource.DYNAMIC_FORM) continue;
            if (!(entry.provider() instanceof PlatformDynamicFormBusinessViewProvider actual)
                    || !"PLATFORM".equals(descriptor.ownerContext())) throw exception(UNAVAILABLE);
            if (formProvider != null && formProvider != actual) throw exception(UNAVAILABLE);
            formProvider = actual;
            dependencies.add(actual.resolveDependency(context, descriptor.dynamicFormRevisionId()));
        }
        if (formProvider != null) formProvider.lockDependencies(context, dependencies);
    }

    private Entry requireEntry(String key, String version) {
        Entry entry = entries.get(new Key(key, version));
        if (entry == null) throw exception(UNAVAILABLE);
        return entry;
    }
    private static Key key(BusinessViewComponentProvider.Component component) {
        return new Key(component.componentKey(), component.componentVersion());
    }
    private static List<String> providerIdentities(BusinessViewComponentProvider.Component c) {
        return List.of("QUERY:" + c.queryProviderKey(), "COMMAND:" + c.commandProviderKey(), "PERMISSION:" + c.permissionProviderKey());
    }
    private static ControlledBusinessViewCatalog.ComponentDescriptor declaration(long tenant, BusinessViewComponentProvider.Component c) {
        return new ControlledBusinessViewCatalog.ComponentDescriptor(tenant, c.componentKey(), c.componentVersion(),
                c.ownerContext(), c.entityType(), BusinessViewDescriptor.ViewSource.valueOf(c.viewSource().name()),
                c.contextSchema(), c.supportedActions(), c.queryProviderKey(), c.commandProviderKey(), c.permissionProviderKey());
    }
    private static ControlledBusinessViewCatalog.ProviderDescriptor provider(Context context,
            BusinessViewComponentProvider.Component c, String key, ControlledBusinessViewCatalog.ProviderKind kind) {
        return new ControlledBusinessViewCatalog.ProviderDescriptor(context.tenantId(), key, kind, c.ownerContext(), c.entityType());
    }
}
