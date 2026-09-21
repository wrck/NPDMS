package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.FileEvidenceService;
import org.junit.jupiter.api.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class FileEvidenceServiceTest {
    final FileArtifactMapper artifacts = mock(FileArtifactMapper.class);
    final FileVersionMapper versions = mock(FileVersionMapper.class);
    final FileReferenceMapper references = mock(FileReferenceMapper.class);
    final FileEvidenceService service = new FileEvidenceService(artifacts, versions, references);
    final FileArtifactDO artifact = new FileArtifactDO();
    final FileVersionDO version = new FileVersionDO();
    final FileReferenceDO reference = new FileReferenceDO();
    final FileEvidenceApi.Query query = new FileEvidenceApi.Query(7L, 40L, 1, "ACC", "PROJECT_DELIVERABLE", "31", "DOCUMENT", "slot", "a".repeat(64));
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        artifact.setId(40L); artifact.setTenantId(7L); artifact.setOwnerContext("ACC"); artifact.setLifecycleStatusCode("ACTIVE"); artifact.setVersion(0);
        version.setArtifactId(40L); version.setTenantId(7L); version.setVersionNo(1); version.setSha256("a".repeat(64)); version.setAvailabilityStatusCode("AVAILABLE");
        reference.setArtifactId(40L); reference.setTenantId(7L); reference.setFileVersionNo(1); reference.setStatusCode("ACTIVE");
        when(artifacts.selectForUpdate(any())).thenReturn(artifact);
        when(versions.selectForUpdate(any())).thenReturn(version);
        when(references.selectForUpdate(any())).thenReturn(reference);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void availableExactVersionAllowsRuleFactWithoutGrantingDownloadAccess() { assertTrue(service.lockAndRevalidate(query).valid()); }
    @Test void revokedOrDetachedEvidenceNeverPasses() {
        version.setAvailabilityStatusCode("UNAVAILABLE"); assertFalse(service.lockAndRevalidate(query).valid());
        version.setAvailabilityStatusCode("AVAILABLE"); reference.setStatusCode("DETACHED"); assertFalse(service.lockAndRevalidate(query).valid());
    }
    @Test void changedReferenceVersionAndContentCannotMasqueradeAsOriginalEvidence() {
        reference.setFileVersionNo(2); assertFalse(service.lockAndRevalidate(query).valid());
        reference.setFileVersionNo(1); version.setSha256("b".repeat(64)); assertFalse(service.lockAndRevalidate(query).valid());
    }
    @Test void archivedExactReferenceStillRetainsValidOriginalFile() { reference.setStatusCode("ARCHIVED"); assertTrue(service.lockAndRevalidate(query).valid()); }
    @Test void tenantMismatchFailsBeforeLocksAndForeignRowsNeverPass() {
        TenantContextHolder.setTenantId(8L); assertThrows(IllegalArgumentException.class, () -> service.lockAndRevalidate(query));
        verify(artifacts, never()).selectForUpdate(any());
        TenantContextHolder.setTenantId(7L); version.setTenantId(8L); assertFalse(service.lockAndRevalidate(query).valid());
    }
}
