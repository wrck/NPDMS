package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FilePreviewServiceTest {
    @Test void visioConvertsExactVersionAndRejectsRevocationDuringConversion() {
        for (String media : new String[]{"application/vnd.visio", "application/vnd.ms-visio.drawing"}) {
            var f = new Fixture(media);
            assertArrayEquals(f.pdf, f.service.preview(f.command).bytes());
            verify(f.visio).convert(argThat(document -> document.mediaType().equals(media)
                    && java.util.Arrays.equals(f.original, document.bytes())));
            verifyNoInteractions(f.office);
            when(f.permissions.hasAnyPermissions(8L, "pms:file:preview")).thenReturn(false);
            assertThrows(RuntimeException.class, () -> f.service.preview(f.command));
            verify(f.artifacts, times(1)).inspect(any());
        }
    }
    @Test void visioRequiresAuthorizedReadBeforeConversion() {
        var f = new Fixture("application/vnd.ms-visio.drawing");
        when(f.content.read(any())).thenThrow(new IllegalArgumentException("denied"));
        assertThrows(RuntimeException.class, () -> f.service.preview(f.command));
        verifyNoInteractions(f.versions, f.office, f.visio, f.artifacts);
    }
    @Test void docAndDocxConvertExactAuthorizedVersionAndNativePdfIsUnchanged() {
        for (String media : new String[]{"application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf"}) {
            var f = new Fixture(media);
            var result = f.service.preview(f.command);
            assertEquals("application/pdf", result.mediaType());
            assertArrayEquals(media.equals("application/pdf") ? f.original : f.pdf, result.bytes());
            verify(f.content).read(argThat(q -> q.file().artifactId() == 61L && q.file().versionNo() == 2
                    && q.file().requiredAction().equals("PREVIEW") && q.maxBytes() == 52_428_800L));
            verify(f.artifacts).inspect(argThat(q -> q.versionNo() == 2 && q.referenceKey().equals("plan")));
            verify(f.audit).record(eq(1L), eq(8L), anyString(), eq("FILE_PREVIEW"), eq("FileArtifact"), eq("61"), eq("SUCCESS"), anyMap());
            if (media.equals("application/pdf")) verifyNoInteractions(f.office);
        }
    }
    @Test void deniedReadDoesNotConvertOrRevealContent() {
        var f = new Fixture("application/msword");
        when(f.content.read(any())).thenThrow(new IllegalArgumentException("denied"));
        assertThrows(RuntimeException.class, () -> f.service.preview(f.command));
        verifyNoInteractions(f.versions, f.office, f.artifacts);
        verify(f.audit, never()).record(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), eq("SUCCESS"), anyMap());
    }
    @Test void conversionFailureAndRevocationDuringConversionDoNotReturnPreview() {
        var failed = new Fixture("application/msword");
        when(failed.office.convert(any())).thenThrow(new IllegalStateException("conversion failed"));
        assertThrows(RuntimeException.class, () -> failed.service.preview(failed.command));
        verifyNoInteractions(failed.artifacts);
        var revoked = new Fixture("application/msword");
        when(revoked.permissions.hasAnyPermissions(8L, "pms:file:preview")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> revoked.service.preview(revoked.command));
        verifyNoInteractions(revoked.artifacts);
        var scopeChanged = new Fixture("application/msword");
        when(scopeChanged.artifacts.inspect(any())).thenThrow(new IllegalArgumentException("scope changed"));
        assertThrows(RuntimeException.class, () -> scopeChanged.service.preview(scopeChanged.command));
    }
    private static class Fixture {
        final AuthenticatedFileContentApi content = mock(AuthenticatedFileContentApi.class);
        final FileArtifactApi artifacts = mock(FileArtifactApi.class);
        final FileVersionMapper versions = mock(FileVersionMapper.class);
        final FileOfficePdfConverter office = mock(FileOfficePdfConverter.class);
        final FileVisioPdfConverter visio = mock(FileVisioPdfConverter.class);
        final OperationAuditApi audit = mock(OperationAuditApi.class);
        final PermissionApi permissions = mock(PermissionApi.class);
        final byte[] original = {1, 2, 3};
        final byte[] pdf = "%PDF-fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        final FileAccessTicketService.AccessCommand command = new FileAccessTicketService.AccessCommand(1L, 8L, 61L, 2, "PREVIEW", "CUT", "CUTOVER_PLAN", "10", "FULL_PLAN", "plan");
        final FilePreviewService service = new FilePreviewService(content, artifacts, versions, office, visio, audit, permissions);
        Fixture(String media) {
            when(content.read(any())).thenReturn(original);
            var row = new FileVersionDO(); row.setDetectedMediaType(media);
            when(versions.selectOne(any())).thenReturn(row);
            when(office.convert(any())).thenReturn(pdf);
            when(visio.convert(any())).thenReturn(pdf);
            when(permissions.hasAnyPermissions(8L, "pms:file:preview")).thenReturn(true);
        }
    }
}
