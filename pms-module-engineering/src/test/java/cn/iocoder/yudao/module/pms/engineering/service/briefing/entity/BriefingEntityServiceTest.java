package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityPageQuery;
import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingAggregate;
import cn.iocoder.yudao.module.pms.engineering.domain.briefing.BriefingDocumentArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.BRIEFING_VERSION_NOT_MATCH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 新副本的应用编排测试；Mock 文件返回值不代表真实文件或数据库验收。 */
@ExtendWith(MockitoExtension.class)
class BriefingEntityServiceTest {
    @Mock private BriefingEntityMapper mapper;
    @Mock private BriefingEntityAccess access;
    @Mock private ObjectProvider<BriefingGenerationPort> ports;
    @Mock private BriefingGenerationPort generator;
    @InjectMocks private BriefingEntityServiceImpl service;
    @BeforeEach void setTenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    private BriefingEntityDO row(int status) {
        var row = new BriefingEntityDO();
        row.setId(7L); row.setTenantId(1L); row.setProjectId(10L);
        row.setCode("BR-7"); row.setName("交底"); row.setStatus(status); row.setVersion(0);
        row.setTemplateId(20L); row.setTemplateSnapshot("{}"); row.setSourceSnapshot("{}");
        row.setContent("原始正文"); row.setFileUrl("/real/file"); row.setFileName("briefing.pdf");
        row.setFileSize(17L); row.setFileChecksum("a".repeat(64));
        return row;
    }
    private void locked(BriefingEntityDO value) {
        when(mapper.selectByTenantAndId(1L,7L)).thenReturn(value);
        when(mapper.selectForUpdate(any())).thenReturn(value);
    }
    private void availableGenerator() {
        when(ports.getIfAvailable()).thenReturn(generator);
        when(access.actorId()).thenReturn(2L);
    }
    private BriefingEntityGenerateReqVO generationRequest(Integer version) {
        var request = new BriefingEntityGenerateReqVO(); request.setId(7L); request.setVersion(version); return request;
    }
    private BriefingDocumentArtifact artifact(long projectId) {
        return new BriefingDocumentArtifact(new BriefingAggregate.Identity(1L,projectId,7L,"BR-7"),
                0,20L,"{}","{}","原始正文","/real/file","briefing.pdf",17L,"a".repeat(64));
    }
    private BriefingEntityDO saved() {
        var captor=ArgumentCaptor.forClass(BriefingEntityDO.class);
        verify(mapper).updateById(captor.capture()); return captor.getValue();
    }
    @Test void createProtectsIdentityStatusAndVersion() {
        var request = new BriefingEntitySaveReqVO();
        request.setId(99L); request.setProjectId(10L); request.setCode("BR-7"); request.setName("交底");
        request.setVersion(99);
        when(mapper.insert(any(BriefingEntityDO.class))).thenAnswer(call -> {
            BriefingEntityDO value=call.getArgument(0);
            assertNull(value.getId()); assertEquals(0,value.getStatus()); assertEquals(0,value.getVersion());
            assertEquals(1L,value.getTenantId()); assertEquals("STANDARD",value.getBriefingType());
            assertNull(value.getLegacySourceId()); value.setId(7L); return 1;
        });
        assertEquals(7L,service.createBriefing(request));
        verify(access).lockWrite(10L,BriefingEntityAccess.CREATE);
    }
    @Test void failedInsertDoesNotReturnSuccess() {
        var request = new BriefingEntitySaveReqVO();
        request.setProjectId(10L); request.setCode("BR-7"); request.setName("交底");
        assertThrows(IllegalStateException.class, () -> service.createBriefing(request));
    }
    @Test void generatePreservesVerifiedContentFileAndVersionUntilMapper() {
        var value=row(0); locked(value); availableGenerator();
        when(generator.generate(any())).thenReturn(artifact(10L));
        when(mapper.updateById(any(BriefingEntityDO.class))).thenReturn(1);
        service.generateBriefing(generationRequest(0));
        var update=saved();
        assertEquals(1,update.getStatus()); assertEquals(0,update.getVersion());
        assertEquals("原始正文",update.getContent()); assertEquals("/real/file",update.getFileUrl());
        assertNotNull(update.getGenerateTime()); assertEquals(0,value.getStatus());
        verify(generator).verify(artifact(10L),2L);
    }
    @Test void generatedBriefingCannotBeEdited() {
        locked(row(1)); var request=new BriefingEntitySaveReqVO(); request.setId(7L);
        assertThrows(ServiceException.class, () -> service.updateBriefing(request));
        verify(mapper,never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void staleVersionDoesNotWrite() {
        locked(row(0));
        var error=assertThrows(ServiceException.class, () -> service.generateBriefing(generationRequest(9)));
        assertEquals(BRIEFING_VERSION_NOT_MATCH.getCode(),error.getCode());
        verify(mapper,never()).updateById(any(BriefingEntityDO.class)); verifyNoInteractions(ports,generator);
    }
    @Test void missingVersionDoesNotBypassConcurrency() {
        locked(row(0));
        var error=assertThrows(ServiceException.class, () -> service.generateBriefing(generationRequest(null)));
        assertEquals(BRIEFING_VERSION_NOT_MATCH.getCode(),error.getCode());
        verify(mapper,never()).updateById(any(BriefingEntityDO.class)); verifyNoInteractions(ports,generator);
    }
    @Test void failedUpdateIsNotReportedAsSuccess() {
        var value=row(2); locked(value); availableGenerator();
        when(mapper.updateById(any(BriefingEntityDO.class))).thenReturn(0);
        var error=assertThrows(ServiceException.class, () -> service.publishBriefing(7L));
        assertEquals(BRIEFING_VERSION_NOT_MATCH.getCode(),error.getCode());
        assertEquals(2,value.getStatus()); assertNull(value.getPublishTime());
        verify(mapper).updateById(any(BriefingEntityDO.class));
    }
    @Test void rejectReturnsToDraftWithActualActorAndOpinion() {
        var value=row(1); locked(value); when(access.actorId()).thenReturn(2L);
        when(mapper.updateById(any(BriefingEntityDO.class))).thenReturn(1);
        var request=new BriefingEntityApproveReqVO(); request.setId(7L); request.setVersion(0);
        request.setApproveAction("REJECT"); request.setApproveOpinion("补充材料"); request.setApproverUserId(999L);
        service.approveBriefing(request); var update=saved();
        assertEquals(0,update.getStatus()); assertEquals("补充材料",update.getApproveOpinion());
        assertEquals(2L,update.getApproverUserId()); assertNotNull(update.getApproveTime());
        assertEquals(1,value.getStatus()); verifyNoInteractions(ports,generator);
    }
    @Test void publishedBriefingCannotBeTerminatedOrDeleted() {
        locked(row(3));
        assertThrows(ServiceException.class, () -> service.terminateBriefing(7L));
        assertThrows(ServiceException.class, () -> service.deleteBriefing(7L));
        verify(mapper,never()).updateById(any(BriefingEntityDO.class)); verify(mapper,never()).deleteById(eq(7L));
    }
    @Test void projectCannotBeChangedByUpdate() {
        locked(row(0)); var request=new BriefingEntitySaveReqVO();
        request.setId(7L); request.setProjectId(20L); request.setCode("BR-7"); request.setVersion(0);
        assertThrows(ServiceException.class, () -> service.updateBriefing(request));
        verify(mapper,never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void unavailableGeneratorLeavesDraftUnchanged() {
        var value=row(0); locked(value);
        var error=assertThrows(IllegalStateException.class, () -> service.generateBriefing(generationRequest(0)));
        assertEquals("BRIEFING_DOCUMENT_GENERATION_NOT_CONNECTED",error.getMessage());
        assertEquals(0,value.getStatus()); assertNull(value.getGenerateTime());
        verify(mapper,never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void failedFileVerificationDoesNotWriteSuccess() {
        var value=row(0); locked(value); availableGenerator();
        when(generator.generate(any())).thenReturn(artifact(10L));
        doThrow(new IllegalStateException("HASH_MISMATCH")).when(generator).verify(any(),eq(2L));
        var error=assertThrows(IllegalStateException.class, () -> service.generateBriefing(generationRequest(0)));
        assertEquals("HASH_MISMATCH",error.getMessage()); assertEquals(0,value.getStatus());
        verify(mapper,never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void wrongArtifactProjectIsRejectedBeforeVerification() {
        locked(row(0)); availableGenerator(); when(generator.generate(any())).thenReturn(artifact(20L));
        var error=assertThrows(IllegalArgumentException.class, () -> service.generateBriefing(generationRequest(0)));
        assertEquals("BRIEFING_DOCUMENT_TARGET_MISMATCH",error.getMessage());
        verify(generator,never()).verify(any(),any()); verify(mapper,never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void objectMoveBetweenReadAndLockIsRejected() {
        var initial=row(0); var moved=row(0); moved.setProjectId(20L);
        when(mapper.selectByTenantAndId(1L,7L)).thenReturn(initial); when(mapper.selectForUpdate(any())).thenReturn(moved);
        assertThrows(ServiceException.class, () -> service.terminateBriefing(7L));
        verify(access).lockWrite(10L,BriefingEntityAccess.UPDATE);
        verify(mapper,never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void deniedPermissionDoesNotReadBusinessRow() {
        doThrow(new IllegalStateException("DENIED")).when(access).requirePermission(BriefingEntityAccess.QUERY);
        var error=assertThrows(IllegalStateException.class, () -> service.getBriefing(7L));
        assertEquals("DENIED",error.getMessage()); verifyNoInteractions(mapper);
    }
    @Test void deniedProjectDoesNotReturnDetails() {
        when(mapper.selectByTenantAndId(1L,7L)).thenReturn(row(0));
        doThrow(new IllegalStateException("PROJECT_DENIED")).when(access).requireReadable(10L);
        var error=assertThrows(IllegalStateException.class, () -> service.getBriefing(7L));
        assertEquals("PROJECT_DENIED",error.getMessage());
    }
    @Test void pageCarriesEmptyTrustedProjectScope() {
        when(access.visibleProjects()).thenReturn(Set.of());
        when(mapper.selectPage(any(BriefingEntityPageQuery.class))).thenReturn(new PageResult<>(List.of(),0L));
        service.getBriefingPage(new BriefingEntityPageReqVO());
        var captor=ArgumentCaptor.forClass(BriefingEntityPageQuery.class); verify(mapper).selectPage(captor.capture());
        assertEquals(Set.of(),captor.getValue().getVisibleProjectIds()); assertEquals(1L,captor.getValue().getTenantId());
    }
    @Test void unboundedPageIsRejectedBeforeMapper() {
        var request=new BriefingEntityPageReqVO(); request.setPageSize(-1);
        assertThrows(IllegalArgumentException.class, () -> service.getBriefingPage(request)); verifyNoInteractions(mapper);
    }
}
