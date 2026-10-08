package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityPresentationApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplatePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EntityPresentationService implements EntityPresentationApi {
    private final EntityProviderRegistry registry;
    private final DynamicFormTemplateMapper templates;
    private final DynamicFormTemplateRevisionMapper revisions;
    private final DynamicFormSchemaService schemas;
    private final cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners owners;

    @Override
    @Transactional(readOnly = true)
    public List<Presentation> list(Query query) {
        query.actor().requireTenant(query.target().entity());
        registry.requireReadable(query.target(), query.actor());
        return catalog(query.actor().tenantId(),query.categoryCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Presentation> listForType(TypeQuery query) {
        if(query==null || query.actor()==null || query.type()==null || query.projectId()==null || query.projectId()<=0)
            throw new IllegalArgumentException("Business presentation scope required");
        var service=owners.byIdentity(query.type().ownerModule(),query.type().entityType())
                .orElseThrow(()->new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("ACCESS_DENIED","Unknown inherited business"));
        // The default service verifies the supplied actor against its trusted caller and project scope.
        service.runtimeActions(new cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext(
                query.actor().tenantId(),query.actor().userId(),query.projectId(),query.type()));
        return catalog(query.actor().tenantId(),query.categoryCode());
    }

    private List<Presentation> catalog(Long tenantId,String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) throw new IllegalArgumentException("Presentation category required");
        List<Presentation> result = new ArrayList<>();
        for (int offset = 0; ; offset += 100) {
            var page = templates.selectPage(new DynamicFormTemplatePageQuery(tenantId, null,
                    categoryCode, "ENABLED", true, offset, 100));
            for (var template : page) {
                var revision = revisions.selectByRow(new DynamicFormRevisionRowQuery(tenantId, template.getCurrentPublishedRevisionId()));
                if (revision == null || !"PUBLISHED".equals(revision.getStatusCode())
                        || !Objects.equals(revision.getTenantId(), tenantId)
                        || !Objects.equals(revision.getTemplateId(), template.getId())) continue;
                DynamicFormSchemaService.SchemaFields schema;
                try {
                    schema = schemas.parseAndValidate(revision.getFormConfJson(), revision.getFormRulesJson(),
                            revision.getEngineCode(), revision.getDesignerVersion(), revision.getRendererVersion());
                } catch (cn.iocoder.yudao.framework.common.exception.ServiceException invalidSchema) {
                    // A malformed published candidate must not hide the other presentations.
                    continue;
                }
                result.add(new Presentation(template.getId(), template.getTemplateName(), revision.getId(),
                        revision.getRevisionNo(), revision.getVersion(), revision.getEngineCode(), revision.getDesignerVersion(),
                        revision.getRendererVersion(), revision.getFormConfJson(), revision.getFormRulesJson(), schema.descriptors()));
            }
            if (page.size() < 100) return List.copyOf(result);
        }
    }
}
