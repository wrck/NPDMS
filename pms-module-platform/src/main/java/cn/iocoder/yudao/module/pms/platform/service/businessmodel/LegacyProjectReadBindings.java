package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.support.access.OwnerProjectReadScopePolicy;
import cn.iocoder.yudao.module.pms.platform.support.access.OwnerParentReadScopePolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import org.springframework.beans.factory.ObjectProvider;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

/**
 * Read-only compatibility for existing project-owned declarations, using the authoritative ProjectScopeApi.
 * Evidence: docs/design/07-authorization-design.md 2.1; each named DO owns its project reference.
 * Customer/company/device/credential/parent-task scopes are deliberately not inferred from a project field.
 */
@Configuration(proxyBeanMethods=false)
public class LegacyProjectReadBindings {
    @Bean public OwnerProjectReadScopePolicy legacyDirectProjectReads(BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, ProjectBusinessScopeAccess projects) {
        return new OwnerProjectReadScopePolicy(Set.of(
                    "COM/deliveryScope",
                    "CUT/cutoverTask",
                    "ACC/acceptance",
                    "ACC/acceptanceActivity",
                    "ACC/acceptanceScopeBinding",
                    "ACC/archiveDocument",
                    "ACC/completionCertificate",
                    "ACC/deliverableChecklist",
                    "ACC/normalClosureApplication",
                    "ACC/satisfactionCollectionTask",
                    "SOL/requirement",
                    "SOL/solution",
                    "SOL/constructionPlan",
                    "SOL/stagePlanBatch",
                    "SOL/briefing",
                    "SOL/resourceReady",
                    "SOL/scheduleBackward",
                    "IMP/training",
                    "IMP/arrival",
                    "IMP/configuration",
                    "IMP/jointTest",
                    "IMP/installation",
                    "IMP/deliveryEvidence",
                    "IMP/deliverable",
                    "IMP/materialExchange",
                    "IMP/materialRequisition",
                    "IMP/externalProcurement",
                    "IMP/issue",
                    "IMP/risk",
                    "RES/outsourceRequest",
                    "PRJ/projectRisk",
                    "PRJ/projectSite",
                    "PRJ/treeChange",
                    "PRJ/planVersion",
                    "PRJ/projectClosure",
                    "PRJ/governanceAction",
                    "PRJ/exitRecord"), "projectId",catalog,persistence,projects);
    }
    @Bean public OwnerProjectReadScopePolicy legacyParentProjectReads(BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, ProjectBusinessScopeAccess projects) {
        return new OwnerProjectReadScopePolicy(Set.of("PRJ/splitRequest"),"parentProjectId",catalog,persistence,projects);
    }
    @Bean public OwnerProjectReadScopePolicy legacyTreeRootReads(BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, ProjectBusinessScopeAccess projects) {
        return new OwnerProjectReadScopePolicy(Set.of("PRJ/treeVersion"),"rootProjectId",catalog,persistence,projects);
    }
    /** CutoverTaskQueryService.detail and its child views authorize ACTION_VIEW on the task's project. */
    @Bean public OwnerParentReadScopePolicy legacyCutoverTaskChildren(BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, ObjectProvider<BusinessEntityAccessPort> access) {
        return new OwnerParentReadScopePolicy(Set.of("CUT/cutoverAssessment","CUT/cutoverPlanRevision","CUT/cutoverChecklist"),
                "cutoverTaskId","CUT","cutoverTask",catalog,persistence,access);
    }
    @Bean public OwnerParentReadScopePolicy legacySatisfactionQuestionnaires(BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, ObjectProvider<BusinessEntityAccessPort> access) {
        return new OwnerParentReadScopePolicy(Set.of("ACC/satisfactionQuestionnaire"),"collectionTaskId","ACC", "satisfactionCollectionTask",catalog,persistence,access);
    }

}
