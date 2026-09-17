package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityImportMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.BriefingEntityImportSource;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query.BriefingEntityLockQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 专用投影承接、内容冲突和删除记录保留测试；真实事务另需 MySQL 验证。 */
@ExtendWith(MockitoExtension.class)
class BriefingEntityImportServiceTest {
    @Mock private BriefingEntityImportMapper mapper;
    @Mock private BriefingEntityAccess access;
    @InjectMocks private BriefingEntityImportService service;
    @BeforeEach void setTenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    private BriefingEntityImportSource source() {
        var source = new BriefingEntityImportSource();
        source.setId(7L); source.setTenantId(1L); source.setCode("BR-7"); source.setProjectId(10L);
        source.setName("原交底"); source.setStatus(3); source.setVersion(8); source.setDeleted(true);
        source.setContent("原文"); source.setFileSize(0L); source.setApproveOpinion("");
        return source;
    }
    private BriefingEntityDO target(BriefingEntityImportSource source) {
        var result = BeanUtils.toBean(source, BriefingEntityDO.class); result.setLegacySourceId(7L); return result;
    }
    @Test void importPreservesDeletedZeroEmptyAndNullAuditFields() {
        var source = source(); var target = target(source);
        when(mapper.selectSource(any())).thenReturn(source);
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectTargetForUpdate(any())).thenReturn(null, target);
        when(mapper.insertImported(source)).thenReturn(1);
        assertEquals(7L, service.importOne(7L));
        assertTrue(target.getDeleted()); assertEquals(0L, target.getFileSize()); assertEquals("", target.getApproveOpinion());
        assertNull(source.getCreator()); assertNull(source.getCreateTime()); assertNull(source.getUpdater());
        assertNull(source.getUpdateTime()); assertEquals(8, source.getVersion()); assertEquals(3, source.getStatus());
        verify(mapper).selectSourceForUpdate(new BriefingEntityLockQuery(1L, 7L));
    }
    @Test void identicalRetryDoesNotInsert() {
        var source = source(); when(mapper.selectSource(any())).thenReturn(source);
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectTargetForUpdate(any())).thenReturn(target(source));
        assertEquals(7L, service.importOne(7L)); verify(mapper, never()).insertImported(any());
    }
    @Test void changedTargetIsNotOverwritten() {
        var source = source(); var target = target(source); target.setContent("目标已编辑");
        when(mapper.selectSource(any())).thenReturn(source);
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectTargetForUpdate(any())).thenReturn(target);
        assertThrows(IllegalStateException.class, () -> service.importOne(7L));
        verify(mapper, never()).insertImported(any()); assertEquals("原文", source.getContent());
    }
    @Test void occupiedPrimaryKeyIsNotReused() {
        var source = source(); var target = target(source); target.setLegacySourceId(null);
        when(mapper.selectSource(any())).thenReturn(source);
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectTargetForUpdate(any())).thenReturn(target);
        assertThrows(IllegalStateException.class, () -> service.importOne(7L));
        verify(mapper, never()).insertImported(any());
    }
    @Test void failedReadbackRejectsImport() {
        var source = source(); var target = target(source); target.setFileChecksum("被改变");
        when(mapper.selectSource(any())).thenReturn(source);
        when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectTargetForUpdate(any())).thenReturn(null, target);
        when(mapper.insertImported(source)).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.importOne(7L));
    }
    @Test void missingSourceDoesNotInsert() {
        assertThrows(RuntimeException.class, () -> service.importOne(7L));
        verify(mapper, never()).insertImported(any()); verify(mapper, never()).selectTargetForUpdate(any());
    }
    @Test void deniedImportDoesNotReadOrLockOldTable() {
        doThrow(new IllegalStateException("DENIED")).when(access).requirePermission(BriefingEntityAccess.CREATE);
        var error=assertThrows(IllegalStateException.class, () -> service.importOne(7L));
        assertEquals("DENIED",error.getMessage()); verifyNoInteractions(mapper);
    }
    @Test void sourceProjectChangeAfterScopeLockDoesNotImport() {
        var initial=source(); var changed=source(); changed.setProjectId(20L);
        when(mapper.selectSource(any())).thenReturn(initial);
        when(mapper.selectSourceForUpdate(any())).thenReturn(changed);
        assertThrows(IllegalStateException.class, () -> service.importOne(7L));
        verify(access).lockWrite(10L,BriefingEntityAccess.CREATE);
        verify(mapper,never()).insertImported(any()); verify(mapper,never()).selectTargetForUpdate(any());
    }
    @Test void foreignTenantSourceIsNotImported() {
        var initial=source(); var changed=source(); changed.setTenantId(2L);
        when(mapper.selectSource(any())).thenReturn(initial);
        when(mapper.selectSourceForUpdate(any())).thenReturn(changed);
        assertThrows(IllegalStateException.class, () -> service.importOne(7L));
        verify(mapper,never()).insertImported(any());
    }

}
