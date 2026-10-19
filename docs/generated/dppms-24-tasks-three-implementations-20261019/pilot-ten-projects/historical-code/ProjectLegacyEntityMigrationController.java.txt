package cn.iocoder.yudao.module.pms.project.controller.admin.legacydetail;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyEntityMigrationService;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectContractCollectionPlanReconciliationService;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyNativeEvidenceService.RowStatus;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/legacy-detail")
@RequiredArgsConstructor
public class ProjectLegacyEntityMigrationController {
    private final ProjectLegacyEntityMigrationService service;
    private final ProjectContractCollectionPlanReconciliationService collectionPlans;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectOrderFieldReconciliationService orderFields;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectProductScopeReconciliationService productScopes;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectExecutionFieldReconciliationService executionFields;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectFactoryRecordReconciliationService factoryRecords;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectInstallationRecordReconciliationService installationRecords;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectRealProductReconciliationService realProducts;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLeaseProductReconciliationService leaseProducts;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectSoftwareHistoryCorrectionService softwareHistory;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectSoftwareLegacyEventWithdrawalService softwareEventWithdrawal;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectShipmentReferenceCorrectionService shipmentReferences;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLicenseRecordMigrationService licenses;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryMigrationService memberHistory;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryImportService memberImports;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectFinalCustomerContactImportService finalContacts;

    @PostMapping("/final-customer-contact-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectFinalCustomerContactImportService.Preview> previewFinalContacts(
            @PathVariable Long projectId,@Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(finalContacts.preview(projectId,request,actor()));
    }
    @PostMapping("/final-customer-contact-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectFinalCustomerContactImportService.Result> importFinalContacts(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectFinalCustomerContactImportService.Request request) {
        return success(finalContacts.migrate(projectId,request,actor()));
    }

    @PostMapping("/member-import-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryImportService.Preview> previewMemberImport(
            @PathVariable Long projectId,@Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(memberImports.preview(projectId,request,actor()));
    }
    @PostMapping("/member-history-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryImportService.Result> importMemberHistory(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryImportService.Request request) {
        return success(memberImports.migrate(projectId,request,actor()));
    }

    @PostMapping("/member-history-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryMigrationService.Report> previewMemberHistory(
            @PathVariable Long projectId,@Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(memberHistory.preview(projectId,request,actor()));
    }
    @PostMapping("/license-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.Preview>> previewLicenses(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLicenseRecordMigrationService.Request request) {
        return success(licenses.preview(projectId,request,actor()));
    }
    @PostMapping("/license-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLicenseRecordMigrationService.Result> importLicenses(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLicenseRecordMigrationService.Request request) {
        return success(licenses.migrate(projectId,request,actor()));
    }
    @PostMapping("/shipment-reference-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi.Baseline>> previewShipmentReferences(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectShipmentReferenceCorrectionService.PreviewRequest request) {
        return success(shipmentReferences.preview(projectId,request,actor()));
    }
    @PostMapping("/shipment-reference-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectShipmentReferenceCorrectionService.Result> correctShipmentReferences(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectShipmentReferenceCorrectionService.CorrectionRequest request) {
        return success(shipmentReferences.correct(projectId,request,actor()));
    }
    @PostMapping("/software-event-withdrawal-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.Baseline>> previewSoftwareEventWithdrawal(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectSoftwareLegacyEventWithdrawalService.PreviewRequest request) {
        return success(softwareEventWithdrawal.preview(projectId,request,actor()));
    }
    @PostMapping("/software-event-withdrawals")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectSoftwareLegacyEventWithdrawalService.Result> withdrawSoftwareEvents(
            @PathVariable Long projectId,@Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectSoftwareLegacyEventWithdrawalService.WithdrawalRequest request) {
        return success(softwareEventWithdrawal.withdraw(projectId,request,actor()));
    }
    @PostMapping("/software-history-correction-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi.Baseline>> previewSoftwareHistory(
            @PathVariable Long projectId,@Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(softwareHistory.preview(projectId,request,actor()));
    }
    @PostMapping("/software-history-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> correctSoftwareHistory(@PathVariable Long projectId,
            @Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectSoftwareHistoryCorrectionService.CorrectionRequest request) {
        return success(softwareHistory.correct(projectId,request,actor()));
    }
    @PostMapping("/lease-product-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLeaseProductReconciliationService.Preview>> previewLeaseProduct(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(leaseProducts.preview(projectId,request,actor()));
    }
    @PostMapping("/lease-product-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileLeaseProduct(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(leaseProducts.reconcile(projectId,request,actor()));
    }
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectProductConfigurationReconciliationService productConfigurations;
    @PostMapping("/product-configuration-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectProductConfigurationReconciliationService.Preview>> previewProductConfiguration(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(productConfigurations.preview(projectId,request,actor()));
    }
    @PostMapping("/product-configuration-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileProductConfiguration(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(productConfigurations.reconcile(projectId,request,actor()));
    }
    @PostMapping("/real-product-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectRealProductReconciliationService.Preview>> previewRealProducts(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(realProducts.preview(projectId,request,actor()));
    }
    @PostMapping("/real-product-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> importRealProducts(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(realProducts.reconcile(projectId,request,actor()));
    }
    @PostMapping("/installation-record-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.Result>> previewInstallationRecords(@PathVariable Long projectId,
            @Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectInstallationRecordReconciliationService.InstallationRequest request) {
        return success(installationRecords.preview(projectId,request,actor()));
    }
    @PostMapping("/installation-record-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileInstallationRecords(@PathVariable Long projectId,
            @Valid @RequestBody cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectInstallationRecordReconciliationService.InstallationRequest request) {
        return success(installationRecords.reconcile(projectId,request,actor()));
    }
    @PostMapping("/factory-record-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileFactoryRecords(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(factoryRecords.reconcile(projectId,request,actor()));
    }
    @PostMapping("/execution-field-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileExecutionFields(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(executionFields.reconcile(projectId,request,actor()));
    }
    @PostMapping("/product-scope-previews")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<java.util.List<cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.Result>> previewProductScopes(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(productScopes.preview(projectId,request,actor()));
    }
    @PostMapping("/product-scope-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileProductScopes(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(productScopes.reconcile(projectId,request,actor()));
    }
    @PostMapping("/order-field-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileOrderFields(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(orderFields.reconcile(projectId,request,actor()));
    }
    @PostMapping("/project-header-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileProjectHeader(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(service.correctProjectHeader(projectId,request,actor()));
    }
    @PostMapping("/collection-plan-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> reconcileCollectionPlans(@PathVariable Long projectId,
            @Valid @RequestBody ProjectContractCollectionPlanReconciliationService.Request request) {
        return success(collectionPlans.reconcile(projectId,request,actor()));
    }
    @PostMapping("/native-imports")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')")
    public CommonResult<ProjectLegacyEntityMigrationService.Result> migrate(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(service.migrate(projectId,request,actor()));
    }
    @PostMapping("/factory-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')")
    public CommonResult<ProjectLegacyEntityMigrationService.FactoryCorrectionResult> correctFactoryVersions(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(service.correctFactoryVersions(projectId,request,actor()));
    }
    @PostMapping("/carrier-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> correctCarrierNames(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.Request request) {
        return success(service.correctCarrierNames(projectId,request,actor()));
    }
    @PostMapping("/installation-candidate-corrections")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update') && @ss.hasPermission('pms:device:assign')")
    public CommonResult<ProjectLegacyEntityMigrationService.CorrectionResult> correctInstallationCandidates(@PathVariable Long projectId,
            @Valid @RequestBody ProjectLegacyEntityMigrationService.InstallationCandidateCorrectionRequest request) {
        return success(service.correctInstallationCandidates(projectId,request,actor()));
    }
    @GetMapping("/native-status")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectLegacyEntityMigrationService.Report> status(@PathVariable Long projectId,
            @RequestParam Long sourceId,@RequestParam(required=false) Long snapshotId) {
        return success(service.status(projectId,sourceId,snapshotId,actor()));
    }
    @GetMapping("/native-record-status")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<Map<String,RowStatus>> records(@PathVariable Long projectId,@RequestParam Long sourceId,
            @RequestParam(required=false) Long snapshotId,@RequestParam String domain,
            @RequestParam(required=false) String sourceKey,@RequestParam(required=false) String parentDomain,
            @RequestParam(required=false) String parentKey,@RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="50") int pageSize) {
        return success(service.rowStatuses(projectId,sourceId,snapshotId,domain,sourceKey,parentDomain,parentKey,pageNo,pageSize,actor()));
    }
    private static ProjectAccessActor actor() {
        return new ProjectAccessActor(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId());
    }
}
