package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileReferenceRespVO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileCurrentReferenceQueryService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileCurrentReferenceQueryServiceTest {
    private final FileBusinessObjectPolicyRegistry policies = mock(FileBusinessObjectPolicyRegistry.class);
    private final FileReferenceMapper references = mock(FileReferenceMapper.class);
    private final FileQueryService files = mock(FileQueryService.class);
    private final FileCurrentReferenceQueryService service = new FileCurrentReferenceQueryService(policies, references, files);
    private final FileQueryService.Actor actor = new FileQueryService.Actor(1L, 8L);
    private final ExactFileReferenceQuery key = new ExactFileReferenceQuery(1L, "CUT", "CUTOVER_CLOSURE", "1400",
            "IMPLEMENTATION_COMMITMENT", "cutover-closure-1400-commitment");

    @BeforeEach
    void setTenant() { TenantContextHolder.setTenantId(1L); }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void recoversOriginalIdentityAndVersionWithoutCreatingOrReplacingTheFile() {
        var stored = new FileReferenceDO();
        stored.setId(355L); stored.setArtifactId(2109805482904100866L); stored.setFileVersionNo(1);
        when(references.selectExact(key)).thenReturn(stored);
        var expected = new FileReferenceRespVO();
        expected.setReferenceId(355L); expected.setArtifactId(stored.getArtifactId()); expected.setVersionNo(1);
        var query = new FileQueryService.ArtifactQuery(stored.getArtifactId(), key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode(), key.referenceKey());
        when(files.getReference(query, actor)).thenReturn(expected);

        assertSame(expected, service.resolve(key, actor));
        verify(policies).inspect(new FileBusinessObjectPolicyQuery(1L, 8L, key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode(), key.referenceKey(), "READ"));
        verify(references).selectExact(key);
        verifyNoMoreInteractions(references);
    }

    @Test
    void deniesScopeBeforeReadingAnyFileReference() {
        when(policies.inspect(any())).thenThrow(exception(FILE_SCOPE_FORBIDDEN));
        assertNull(service.resolve(key, actor));
        verifyNoInteractions(references, files);
    }

    @Test
    void rejectsForeignTenantBeforeAuthorizationOrLookup() {
        var foreign = new ExactFileReferenceQuery(2L, key.ownerContext(), key.objectType(), key.objectId(),
                key.purposeCode(), key.referenceKey());
        assertEquals(FILE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.resolve(foreign, actor)).getCode());
        verifyNoInteractions(policies, references, files);
    }

    @Test
    void missingStableKeyNeverBecomesAnUnfilteredLookup() {
        var missing = new ExactFileReferenceQuery(1L, key.ownerContext(), key.objectType(), key.objectId(),
                key.purposeCode(), " ");
        assertEquals(FILE_COMMAND_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.resolve(missing, actor)).getCode());
        verifyNoInteractions(policies, references, files);
    }

    @Test
    void absentSlotDoesNotChooseAnotherReference() {
        assertNull(service.resolve(key, actor));
        verify(references).selectExact(key);
        verifyNoInteractions(files);
    }

    @Test
    void unavailableOwnerDoesNotFallBackToAFileLookup() {
        when(policies.inspect(any())).thenThrow(exception(FILE_PROVIDER_UNAVAILABLE));
        assertEquals(FILE_PROVIDER_UNAVAILABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.resolve(key, actor)).getCode());
        verifyNoInteractions(references, files);
    }
}
