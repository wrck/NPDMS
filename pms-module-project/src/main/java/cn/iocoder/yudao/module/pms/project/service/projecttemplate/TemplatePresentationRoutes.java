package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplatePresentationContract;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplatePresentationUrl;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Uses the existing Owner component directory; registering a path never reads or creates a business object. */
@Component
public final class TemplatePresentationRoutes {
    private final Map<String, BusinessViewComponentProvider.Component> routes;

    public TemplatePresentationRoutes(List<BusinessViewComponentProvider> providers) {
        Map<String, BusinessViewComponentProvider.Component> registered = new LinkedHashMap<>();
        for (var provider : providers) {
            var paths = Objects.requireNonNull(provider.pagePaths(), "page paths");
            if (paths.isEmpty()) continue;
            var component = Objects.requireNonNull(provider.component(), "page component");
            if (component.viewSource() != BusinessViewComponentProvider.ViewSource.PAGE
                    || !text(component.ownerContext()) || !text(component.entityType())
                    || !text(component.componentKey()) || !text(component.componentVersion()))
                throw new IllegalArgumentException("PRESENTATION_COMPONENT_INVALID");
            for (String path : paths) {
                if (registered.putIfAbsent(TemplatePresentationUrl.path(path), component) != null)
                    throw new IllegalArgumentException("PRESENTATION_ROUTE_DUPLICATE: " + path);
            }
        }
        routes = Map.copyOf(registered);
    }

    public void validate(TemplateExecutionConfiguration.Presentation presentation,
            TemplateDesignerDocument.WorkBindingSpec binding) {
        if (binding == null || binding.getDynamicFormRevisionId() != null)
            throw new IllegalArgumentException("PRESENTATION_BINDING_REQUIRED");
        TemplatePresentationContract.validate(presentation, binding.getTargetContextCode(), binding.getTargetObjectType(),
                binding.getComponentKey(), binding.getBusinessViewSnapshot());
        requireDeployed(presentation.pageUrl(), binding.getBusinessViewSnapshot());
    }

    public void validate(TemplateExecutionConfiguration.Presentation presentation,
            TemplateExecutionSnapshot.BindingContract binding) {
        TemplatePresentationContract.validate(presentation, binding);
        requireDeployed(presentation.pageUrl(), binding.getBusinessViewSnapshot());
    }

    private void requireDeployed(String path, JsonNode view) {
        var component = routes.get(path);
        if (component == null) throw new IllegalArgumentException("PRESENTATION_ROUTE_NOT_INSTALLED");
        if (!component.ownerContext().equals(view.path("ownerContext").asText())
                || !component.entityType().equals(view.path("entityType").asText())
                || !component.componentKey().equals(view.path("componentKey").asText())
                || !component.componentVersion().equals(view.path("componentVersion").asText()))
            throw new IllegalArgumentException("PRESENTATION_ROUTE_BINDING_MISMATCH");
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
}
