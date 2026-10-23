package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantGeneratedFilePolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompletionConfirmationGeneratedFilePolicyTest {
    @Mock SatisfactionAccessGrantMapper grants;
    @Mock CompletionSatisfactionBundleMapper bundles;
    @Mock CompletionSatisfactionSigningMapper signings;
    @Mock CompletionCertificateMapper certificates;
    @Mock PermissionApi permissions;
    @Mock ProjectScopeApi scopes;
    @Mock ProjectAcceptanceContextApi projects;
    @InjectMocks CompletionConfirmationGeneratedFilePolicy policy;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var certificate=new CompletionCertificateDO();certificate.setId(10L);certificate.setTenantId(1L);
        certificate.setProjectId(9L);certificate.setStatus(2);certificate.setVersion(4L);
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        var grant=new SatisfactionAccessGrantDO();grant.setId(20L);grant.setTenantId(1L);
        grant.setGrantStatus("ACTIVE");grant.setGrantVersion(1);grant.setCompletionBundleId(30L);
        grant.setQuestionnaireId(40L);grant.setCreator("50");
        grant.setEffectiveFrom(LocalDateTime.now().minusMinutes(1));grant.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(grants.selectByIdForUpdate(1L,20L)).thenReturn(grant);
        var bundle=new CompletionSatisfactionBundleDO();bundle.setId(30L);bundle.setTenantId(1L);
        bundle.setCertificateId(10L);bundle.setProjectId(9L);
        when(bundles.selectById(30L)).thenReturn(bundle);
        var signing=new CompletionSatisfactionSigningDO();signing.setGrantId(20L);
        when(signings.selectByQuestionnaire(any())).thenReturn(signing);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    private BusinessGrantGeneratedFilePolicyQuery query() {
        return new BusinessGrantGeneratedFilePolicyQuery(1L,"ACC","COMPLETION_CONFIRMATION",10L,4L,20L,1L,"COMPLETION_HTML/4",7L);
    }
    @Test void signedCustomerDocumentsRequireSubmitButNotAudit() {
        when(permissions.hasAnyPermissions(50L,"pms:acc-completion-certificate:submit")).thenReturn(true);
        var scope=new ProjectScopeResult(9L,7L,Set.of(9L),Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope);when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        var project=new ProjectAcceptanceContextApi.Context(9L,9L,5L,7L,"ACTIVE");
        when(projects.inspect(any())).thenReturn(project);when(projects.lock(any(),any(),any())).thenReturn(project);
        assertNotNull(policy.lockAndRevalidate(query()));
        verify(permissions,never()).hasAnyPermissions(50L,"pms:acc-completion-certificate:audit");
    }
    @Test void revokedSubmitPermissionRejectsBeforeProjectOrFileGeneration() {
        assertThrows(RuntimeException.class,()->policy.lockAndRevalidate(query()));
        verify(permissions).hasAnyPermissions(50L,"pms:acc-completion-certificate:submit");
        verifyNoInteractions(scopes,projects);
    }
}
