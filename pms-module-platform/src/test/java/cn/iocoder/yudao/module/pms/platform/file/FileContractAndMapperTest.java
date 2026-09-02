package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFilePolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileAccessGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArchiveRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileContractAndMapperTest {

    @Test
    void closesActionAndReferenceKeyAtThePublicQueryBoundary() {
        assertEquals(9, FileActionCodes.SUPPORTED_ACTIONS.size());
        assertThrows(IllegalArgumentException.class, () -> query("UNKNOWN", "slot-a"));
        assertThrows(IllegalArgumentException.class, () -> query(FileActionCodes.READ, " "));

        var revalidation = new FileArtifactVersionRevalidationQuery(
                11L, 2, "SOL", "CHANGE", "900", "EVIDENCE", "slot-a",
                FileActionCodes.READ, new FileFactVersion(1, 2, 3), 4L);
        assertEquals("slot-a", revalidation.referenceKey());
        assertEquals(2, revalidation.toInspectionQuery().versionNo());
    }

    @Test
    void resolvesOneExactProviderAndRevalidatesTheScopeVersion() {
        FileBusinessObjectPolicyProvider provider = provider("SOL", "CHANGE");
        FileBusinessObjectPolicyQuery query = policyQuery(FileActionCodes.UPLOAD);
        FileBusinessObjectPolicyRevalidationQuery revalidation = new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "SOL", "CHANGE", "900", "EVIDENCE", "slot-a",
                FileActionCodes.UPLOAD, 7L);
        when(provider.inspect(query)).thenReturn(allowedFact(7L));
        when(provider.lockAndRevalidate(revalidation)).thenReturn(allowedFact(7L));
        FileBusinessObjectPolicyRegistry registry = new FileBusinessObjectPolicyRegistry(List.of(provider));

        assertEquals(7L, registry.inspect(query).scopeVersion());
        assertEquals(7L, registry.lockAndRevalidate(revalidation).scopeVersion());
    }

    @Test
    void dispatchesGeneratedFilePolicyAndRejectsChangedScopeVersion() {
        FileBusinessObjectPolicyProvider provider = provider("ACC", "SATISFACTION_RESULT");
        GeneratedBusinessFilePolicyRevalidationQuery query = new GeneratedBusinessFilePolicyRevalidationQuery(
                0L, 9L, 40L, 10L, 11L, 12L, 4,
                "ACC", "SATISFACTION_RESULT", "SATISFACTION_RESULT_DOCUMENT",
                "satisfaction-result-40", FileActionCodes.UPLOAD, 7L);
        when(provider.lockAndRevalidateGeneratedBusinessFile(query)).thenReturn(allowedFact(7L));
        FileBusinessObjectPolicyRegistry registry = new FileBusinessObjectPolicyRegistry(List.of(provider));

        assertEquals(7L, registry.lockAndRevalidateGeneratedBusinessFile(query).scopeVersion());

        when(provider.lockAndRevalidateGeneratedBusinessFile(query)).thenReturn(allowedFact(8L));
        assertCode(FILE_SCOPE_VERSION_CONFLICT.getCode(),
                () -> registry.lockAndRevalidateGeneratedBusinessFile(query));
    }

    @Test
    void failsClosedForMissingMultipleDeniedInvalidAndChangedProviders() {
        FileBusinessObjectPolicyQuery query = policyQuery(FileActionCodes.READ);
        assertCode(FILE_PROVIDER_UNAVAILABLE.getCode(),
                () -> new FileBusinessObjectPolicyRegistry(List.of()).inspect(query));

        FileBusinessObjectPolicyProvider first = provider("SOL", "CHANGE");
        FileBusinessObjectPolicyProvider second = provider("SOL", "CHANGE");
        assertCode(FILE_PROVIDER_UNAVAILABLE.getCode(),
                () -> new FileBusinessObjectPolicyRegistry(List.of(first, second)).inspect(query));

        FileBusinessObjectPolicyProvider brokenKey = provider("SOL", "CHANGE");
        doThrow(new IllegalStateException("provider key failed")).when(brokenKey).ownerContext();
        assertCode(FILE_PROVIDER_UNAVAILABLE.getCode(),
                () -> new FileBusinessObjectPolicyRegistry(List.of(brokenKey)).inspect(query));

        doThrow(new IllegalStateException("provider failed")).when(first).inspect(query);
        assertCode(FILE_PROVIDER_UNAVAILABLE.getCode(),
                () -> new FileBusinessObjectPolicyRegistry(List.of(first)).inspect(query));

        doReturn(new FileBusinessObjectPolicyFact(
                false, 7L, null, null, null, null, null, null))
                .when(first).inspect(query);
        assertCode(FILE_SCOPE_FORBIDDEN.getCode(),
                () -> new FileBusinessObjectPolicyRegistry(List.of(first)).inspect(query));

        FileBusinessObjectPolicyRevalidationQuery changed = new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "SOL", "CHANGE", "900", "EVIDENCE", "slot-a",
                FileActionCodes.READ, 6L);
        when(first.lockAndRevalidate(changed)).thenReturn(allowedFact(7L));
        assertCode(FILE_SCOPE_VERSION_CONFLICT.getCode(),
                () -> new FileBusinessObjectPolicyRegistry(List.of(first)).lockAndRevalidate(changed));
    }

    @Test
    void exposesOnlyScenarioMapperMethodsAndNoGenericCrud() {
        assertMapperMethods(FileArtifactMapper.class,
                Set.of("insert", "selectOne", "selectForUpdate", "activateDraftIfMatch",
                        "updateLifecycleIfMatch"));
        assertMapperMethods(FileVersionMapper.class,
                Set.of("insert", "selectOne", "selectForUpdate", "selectCursor",
                        "selectByInfraFileIdForUpdate", "updateAvailabilityIfMatch"));
        assertMapperMethods(FileReferenceMapper.class,
                Set.of("insert", "selectExact", "selectForUpdate", "selectCursor", "replaceVersionIfMatch",
                        "updateStateIfMatch", "selectByArtifactForUpdate", "selectActiveSet",
                        "selectSetForUpdate"));
        assertMapperMethods(FileUploadSessionMapper.class,
                Set.of("insert", "selectForUpdate", "selectArtifactBindingForUpdate",
                        "selectBusinessGrantSlotsForUpdate",
                        "beginValidationIfInitialized", "bindStorageReceiptIfInitialized",
                        "completeIfValidating", "terminateIfRetryable"));
        assertMapperMethods(FileAccessGrantMapper.class, Set.of("insert"));
        assertMapperMethods(FileArchiveRecordMapper.class, Set.of("insert", "selectOne"));
    }

    @Test
    void protectsTheValidatedScanContentFromProviderMutation() {
        byte[] content = {1, 2, 3};
        FileSecurityScanCommand command = new FileSecurityScanCommand(
                content, "a.pdf", "application/pdf", "application/pdf", "a".repeat(64));
        content[0] = 9;
        byte[] returned = command.validatedContent();
        returned[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, command.validatedContent());
    }

    private static FileArtifactVersionQuery query(String action, String referenceKey) {
        return new FileArtifactVersionQuery(
                11L, 2, "SOL", "CHANGE", "900", "EVIDENCE", referenceKey, action);
    }

    private static FileBusinessObjectPolicyQuery policyQuery(String action) {
        return new FileBusinessObjectPolicyQuery(
                0L, 9L, "SOL", "CHANGE", "900", "EVIDENCE", "slot-a", action);
    }

    private static FileBusinessObjectPolicyProvider provider(String ownerContext, String objectType) {
        FileBusinessObjectPolicyProvider provider = mock(FileBusinessObjectPolicyProvider.class);
        when(provider.ownerContext()).thenReturn(ownerContext);
        when(provider.objectType()).thenReturn(objectType);
        return provider;
    }

    private static FileBusinessObjectPolicyFact allowedFact(Long scopeVersion) {
        return new FileBusinessObjectPolicyFact(true, scopeVersion, "MUTABLE", "SINGLE",
                Set.of("EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }

    private static void assertMapperMethods(Class<?> mapper, Set<String> expected) {
        assertFalse(BaseMapper.class.isAssignableFrom(mapper));
        Set<String> actual = Arrays.stream(mapper.getDeclaredMethods())
                .map(method -> method.getName()).collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    private static void assertCode(int expectedCode, Runnable invocation) {
        ServiceException failure = assertThrows(ServiceException.class, invocation::run);
        assertEquals(expectedCode, failure.getCode());
    }
}
