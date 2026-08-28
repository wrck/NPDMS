package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionItem;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ExistingFileReferenceTarget;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.service.file.ExistingFileVersionAttachmentService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExistingFileVersionAttachmentServiceTest {

    private static final String TARGET_SLOT = "5d3ea761-184b-4dab-8b88-8a487f55621e";

    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock FileArtifactMapper artifactMapper;
    @Mock FileVersionMapper versionMapper;
    @Mock FileReferenceMapper referenceMapper;
    @Mock FileEventFactory eventFactory;
    @Mock PlatformTransactionalOutboxWriter outboxWriter;

    private ExistingFileVersionAttachmentService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        LoginUser user = new LoginUser();
        user.setId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        service = new ExistingFileVersionAttachmentService(policyRegistry, artifactMapper, versionMapper,
                referenceMapper, eventFactory, outboxWriter);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void locksAllProvidersBeforePlatformFactsAndWritesOneEventForInsert() {
        when(policyRegistry.lockAndRevalidate(any())).thenAnswer(invocation -> {
            FileBusinessObjectPolicyRevalidationQuery query = invocation.getArgument(0);
            return FileActionCodes.READ.equals(query.requiredAction()) ? sourcePolicy() : targetPolicy();
        });
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.selectForUpdate(any())).thenAnswer(invocation -> {
            FileReferenceLockQuery query = invocation.getArgument(0);
            return "100".equals(query.objectId()) ? sourceReference() : null;
        });
        doAnswer(invocation -> {
            FileReferenceDO row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        }).when(referenceMapper).insert(any(FileReferenceDO.class));
        when(eventFactory.referenceAttached(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.BusinessEvent(
                        "event-1", "FileReferenceAttached", "{\"eventId\":\"event-1\"}"));

        var result = service.attach(command());

        assertEquals(1, result.size());
        assertEquals(TARGET_SLOT, result.getFirst().referenceKey());
        assertEquals(new FileFactVersion(3, 0, 5), result.getFirst().fileFactVersion());
        InOrder order = inOrder(policyRegistry, artifactMapper, versionMapper, referenceMapper);
        order.verify(policyRegistry, times(2)).lockAndRevalidateReferenceSet(any());
        order.verify(policyRegistry, times(2)).lockAndRevalidate(any());
        order.verify(artifactMapper).selectForUpdate(any());
        order.verify(versionMapper).selectForUpdate(any());
        order.verify(referenceMapper, times(2)).selectForUpdate(any());
        verify(referenceMapper).insert(any(FileReferenceDO.class));
        verify(outboxWriter).write(any(), any(), any(), any(), any());
    }

    @Test
    void identicalTargetReplaysWithoutInsertOrEvent() {
        when(policyRegistry.lockAndRevalidate(any())).thenAnswer(invocation -> {
            FileBusinessObjectPolicyRevalidationQuery query = invocation.getArgument(0);
            return FileActionCodes.READ.equals(query.requiredAction()) ? sourcePolicy() : targetPolicy();
        });
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact());
        when(versionMapper.selectForUpdate(any())).thenReturn(version());
        when(referenceMapper.selectForUpdate(any())).thenAnswer(invocation -> {
            FileReferenceLockQuery query = invocation.getArgument(0);
            return "100".equals(query.objectId()) ? sourceReference() : targetReference();
        });

        var result = service.attach(command());

        assertEquals(new FileFactVersion(3, 2, 5), result.getFirst().fileFactVersion());
        verify(referenceMapper, never()).insert(any(FileReferenceDO.class));
        verify(outboxWriter, never()).write(any(), any(), any(), any(), any());
    }

    private AttachExistingFileVersionsCommand command() {
        var source = new FileArtifactVersionRevalidationQuery(11L, 2, "SOL",
                "REQUIREMENT_ANALYSIS_SECTION", "100", "SECTION_ATTACHMENT", "source-slot",
                FileActionCodes.READ, new FileFactVersion(3, 4, 5), 8L);
        var target = new ExistingFileReferenceTarget("SOL", "REQUIREMENT_ANALYSIS_SECTION",
                "200", "SECTION_ATTACHMENT", TARGET_SLOT, 9L);
        return new AttachExistingFileVersionsCommand("operation-1",
                List.of(new AttachExistingFileVersionItem(source, target)));
    }

    private FileBusinessObjectPolicyFact sourcePolicy() {
        return new FileBusinessObjectPolicyFact(true, 8L, "IMMUTABLE", "MULTIPLE",
                Set.of("EVIDENCE"), Set.of("application/pdf"), 100L, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact targetPolicy() {
        return new FileBusinessObjectPolicyFact(true, 9L, "MUTABLE", "MULTIPLE",
                Set.of("EVIDENCE"), Set.of("application/pdf"), 100L, "INTERNAL");
    }

    private FileArtifactDO artifact() {
        FileArtifactDO row = new FileArtifactDO();
        row.setId(11L);
        row.setName("evidence.pdf");
        row.setOwnerContext("SOL");
        row.setCategoryCode("EVIDENCE");
        row.setLifecycleStatusCode("ACTIVE");
        row.setVersion(3);
        return row;
    }

    private FileVersionDO version() {
        FileVersionDO row = new FileVersionDO();
        row.setArtifactId(11L);
        row.setVersionNo(2);
        row.setSizeBytes(10L);
        row.setDetectedMediaType("application/pdf");
        row.setSha256("a".repeat(64));
        row.setAvailabilityStatusCode("AVAILABLE");
        row.setAvailabilityVersion(5);
        return row;
    }

    private FileReferenceDO sourceReference() {
        FileReferenceDO row = targetReference();
        row.setId(21L);
        row.setObjectId("100");
        row.setReferenceKey("source-slot");
        row.setScopeVersion(8L);
        row.setVersion(4);
        return row;
    }

    private FileReferenceDO targetReference() {
        FileReferenceDO row = new FileReferenceDO();
        row.setId(31L);
        row.setObjectId("200");
        row.setReferenceKey(TARGET_SLOT);
        row.setArtifactId(11L);
        row.setFileVersionNo(2);
        row.setStatusCode("ACTIVE");
        row.setScopeVersion(9L);
        row.setVersion(2);
        return row;
    }
}
