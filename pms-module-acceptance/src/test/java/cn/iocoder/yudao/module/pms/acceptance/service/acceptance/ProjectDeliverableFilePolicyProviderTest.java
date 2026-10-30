package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableFilePolicyProvider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * V374 历史交付件文档锚只读策略：ACC/PROJECT_DELIVERABLE 锚保留 READ/DOWNLOAD/PREVIEW，
 * 上传/替换/解除一律拒绝（统一上传统走 PLT/DELIVERY_MATERIAL 平台锚）。
 */
class ProjectDeliverableFilePolicyProviderTest {

    final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    final ProjectDeliverableAccess access = mock(ProjectDeliverableAccess.class);
    final FileBusinessObjectPolicyProvider provider =
            new ProjectDeliverableFilePolicyProvider(platform, rules, access);
    final FileReferenceSetKey key = new FileReferenceSetKey(OWNER, TYPE, "20", PURPOSE);
    final TemplateFrozenView view = new TemplateFrozenView(20L, 9L, "S1_D3", "需求文档", "S1", null,
            null, null, true, 1, null, "SATISFIED", "{}", 2);

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        when(platform.findById(20L)).thenReturn(Optional.of(view));
        when(platform.lockById(20L)).thenReturn(Optional.of(view));
        var context = new ProjectDeliverableRuleApi.Context(9L, 10L, "ACTIVE", 11L, "S1", null,
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree("{}"));
        when(rules.read(9L, "S1_D3")).thenReturn(context);
        when(rules.lock(9L, "S1_D3")).thenReturn(context);
        when(access.check(any(), eq(7L), eq(11L), eq(false), anyBoolean(), any())).thenReturn(77L);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void readActionsPassThroughOwnerScopeAndUploadIsAlwaysDenied() {
        var inspected = provider.inspect(new FileBusinessObjectPolicyQuery(7L, 11L, OWNER, TYPE, "20", PURPOSE,
                "file-slot", "READ"));
        assertTrue(inspected.allowed());
        assertEquals(77L, inspected.scopeVersion());
        assertEquals("IMMUTABLE", inspected.referenceMutability());
        assertTrue(inspected.allowedMediaTypes().contains("application/pdf"));

        var set = provider.lockAndRevalidateReferenceSet(new FileBusinessObjectReferenceSetRevalidationQuery(
                7L, 11L, key, "DOWNLOAD", 77L));
        var slot = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(7L, 11L, OWNER,
                TYPE, "20", PURPOSE, "file-slot", "PREVIEW", 77L));
        assertEquals(inspected, set);
        assertEquals(set, slot);
        // 引用集与单文件两次锁级重验都沿 Owner 数据范围（期望范围 77）放行。
        verify(access, times(2)).check(any(), eq(7L), eq(11L), eq(false), eq(true), eq(77L));

        assertFalse(provider.inspect(new FileBusinessObjectPolicyQuery(7L, 11L, OWNER, TYPE, "20", PURPOSE,
                "file-slot", "UPLOAD")).allowed());
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "UPLOAD")).allowed());
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "REPLACE")).allowed());
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(7L, 11L, key, "DETACH")).allowed());
    }

    @Test
    void foreignKeysTenantsPurposesAndMissingRowsAreDenied() {
        assertFalse(provider.inspectReferenceSet(
                new FileBusinessObjectReferenceSetQuery(7L, 11L, new FileReferenceSetKey("SOL", TYPE, "20", PURPOSE), "READ"))
                .allowed());
        assertFalse(provider.inspectReferenceSet(
                new FileBusinessObjectReferenceSetQuery(7L, 11L, new FileReferenceSetKey(OWNER, TYPE, "20", "OTHER"), "READ"))
                .allowed());
        assertFalse(provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(8L, 11L, key, "READ")).allowed());
        assertFalse(provider.inspect(new FileBusinessObjectPolicyQuery(7L, 11L, OWNER, TYPE, "not-a-number",
                PURPOSE, "file-slot", "READ")).allowed());
        when(platform.findById(20L)).thenReturn(Optional.empty());
        assertFalse(provider.inspect(new FileBusinessObjectPolicyQuery(7L, 11L, OWNER, TYPE, "20", PURPOSE,
                "file-slot", "READ")).allowed());
    }

    @Test
    void staleOwnerScopeFailsTheLockRevalidation() {
        when(access.check(any(), eq(7L), eq(11L), eq(false), eq(true), eq(1L)))
                .thenThrow(new RuntimeException("SCOPE_CHANGED"));
        assertThrows(RuntimeException.class, () -> provider.lockAndRevalidateReferenceSet(
                new FileBusinessObjectReferenceSetRevalidationQuery(7L, 11L, key, "READ", 1L)));
    }
}
