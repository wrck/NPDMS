package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 新副本测试；不修改、替换或降低旧实现的测试。 */
@ExtendWith(MockitoExtension.class)
class BriefingEntityServiceTest {
    @Mock private BriefingEntityMapper mapper;
    @InjectMocks private BriefingEntityServiceImpl service;
    @BeforeEach void setTenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    private BriefingEntityDO row(int status) {
        var row = new BriefingEntityDO();
        row.setId(7L); row.setTenantId(1L); row.setProjectId(10L);
        row.setCode("BR-7"); row.setName("交底"); row.setStatus(status); row.setVersion(0);
        return row;
    }
    @Test void createProtectsIdentityStatusAndVersion() {
        var request = new BriefingEntitySaveReqVO();
        request.setId(99L); request.setProjectId(10L); request.setCode("BR-7"); request.setName("交底");
        request.setVersion(99);
        when(mapper.insert(any(BriefingEntityDO.class))).thenAnswer(call -> {
            BriefingEntityDO value = call.getArgument(0);
            assertNull(value.getId()); assertEquals(0, value.getStatus()); assertEquals(0, value.getVersion());
            assertEquals(1L, value.getTenantId()); assertEquals("STANDARD", value.getBriefingType());
            assertNull(value.getLegacySourceId()); value.setId(7L); return 1;
        });
        assertEquals(7L, service.createBriefing(request));
    }
    @Test void generatePreservesExistingContentFileAndVersionUntilMapper() {
        var value = row(0); value.setContent("原始正文"); value.setFileUrl("/real/file");
        when(mapper.selectForUpdate(any())).thenReturn(value);
        when(mapper.updateById(any(BriefingEntityDO.class))).thenReturn(1);
        var request = new BriefingEntityGenerateReqVO(); request.setId(7L); request.setVersion(0);
        service.generateBriefing(request);
        assertEquals(1, value.getStatus()); assertEquals(0, value.getVersion());
        assertEquals("原始正文", value.getContent()); assertEquals("/real/file", value.getFileUrl());
        assertNotNull(value.getGenerateTime());
    }
    @Test void generatedBriefingCannotBeEdited() {
        when(mapper.selectForUpdate(any())).thenReturn(row(1));
        var request = new BriefingEntitySaveReqVO(); request.setId(7L);
        assertThrows(RuntimeException.class, () -> service.updateBriefing(request));
        verify(mapper, never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void staleVersionDoesNotWrite() {
        when(mapper.selectForUpdate(any())).thenReturn(row(0));
        var request = new BriefingEntityGenerateReqVO(); request.setId(7L); request.setVersion(9);
        assertThrows(RuntimeException.class, () -> service.generateBriefing(request));
        verify(mapper, never()).updateById(any(BriefingEntityDO.class));
    }
    @Test void failedUpdateIsNotReportedAsSuccess() {
        when(mapper.selectForUpdate(any())).thenReturn(row(2));
        when(mapper.updateById(any(BriefingEntityDO.class))).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.publishBriefing(7L));
    }
    @Test void rejectReturnsToDraftAndRetainsOpinion() {
        var value = row(1);
        when(mapper.selectForUpdate(any())).thenReturn(value);
        when(mapper.updateById(any(BriefingEntityDO.class))).thenReturn(1);
        var request = new BriefingEntityApproveReqVO(); request.setId(7L); request.setApproveAction("REJECT");
        request.setApproveOpinion("补充材料"); request.setApproverUserId(2L);
        service.approveBriefing(request);
        assertEquals(0, value.getStatus()); assertEquals("补充材料", value.getApproveOpinion());
        assertEquals(2L, value.getApproverUserId()); assertNotNull(value.getApproveTime());
    }
    @Test void publishedBriefingCannotBeTerminatedOrDeleted() {
        when(mapper.selectForUpdate(any())).thenReturn(row(3));
        assertThrows(RuntimeException.class, () -> service.terminateBriefing(7L));
        assertThrows(RuntimeException.class, () -> service.deleteBriefing(7L));
        verify(mapper, never()).updateById(any(BriefingEntityDO.class));
        verify(mapper, never()).deleteById(eq(7L));
    }
}
