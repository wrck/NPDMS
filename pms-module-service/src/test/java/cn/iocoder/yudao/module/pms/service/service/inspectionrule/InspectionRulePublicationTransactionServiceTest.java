package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleProductTypeNameUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRulePublishUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRulePublicationTransactionServiceTest {

    private static final long TENANT_ID = 7L;
    private static final long RULE_ID = 10L;
    private static final long TARGET_ID = 20L;
    private static final long CURRENT_ID = 21L;
    private static final long ACTOR_ID = 9L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 9, 2, 17, 30);

    @Mock
    private InspectionRuleRevisionMapper revisionMapper;
    @Mock
    private InspectionRuleProductTypeRevisionMapper productTypeMapper;

    private InspectionRulePublicationTransactionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionRulePublicationTransactionService(revisionMapper, productTypeMapper);
    }

    @Test
    void shouldRefreshSnapshotsAndAtomicallyReplaceCurrentPublishedRevision() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", CURRENT_ID, 1, 4));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(
                productType("OLD-A", "A"), productType("OLD-B", "B")));
        when(productTypeMapper.updateNameSnapshot(any())).thenReturn(1);
        when(revisionMapper.disablePublishedIfMatch(any())).thenReturn(1);
        when(revisionMapper.publishDraftIfMatch(any())).thenReturn(1);

        InspectionRulePublicationTransactionService.PublishResult result = service.publishVerified(
                command(Map.of("A", "权威A", "B", "权威B")));

        assertEquals(TARGET_ID, result.revisionId());
        assertEquals(4, result.version());
        assertEquals(CURRENT_ID, result.disabledRevisionId());
        InOrder writes = inOrder(productTypeMapper, revisionMapper);
        writes.verify(productTypeMapper).updateNameSnapshot(new InspectionRuleProductTypeNameUpdate(
                TENANT_ID, TARGET_ID, "A", "权威A"));
        writes.verify(productTypeMapper).updateNameSnapshot(new InspectionRuleProductTypeNameUpdate(
                TENANT_ID, TARGET_ID, "B", "权威B"));
        writes.verify(revisionMapper).disablePublishedIfMatch(new InspectionRuleDisableUpdate(
                TENANT_ID, CURRENT_ID, 4, ACTOR_ID, PUBLISHED_AT));
        writes.verify(revisionMapper).publishDraftIfMatch(new InspectionRulePublishUpdate(
                TENANT_ID, TARGET_ID, 3, "权威分类", "权威严重度", ACTOR_ID, PUBLISHED_AT));
    }

    @Test
    void shouldPublishWithoutDisablingWhenNoCurrentRevisionExists() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", null, null, null));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(
                productType("OLD-A", "A"), productType("OLD-B", "B")));
        when(productTypeMapper.updateNameSnapshot(any())).thenReturn(1);
        when(revisionMapper.publishDraftIfMatch(any())).thenReturn(1);

        InspectionRulePublicationTransactionService.PublishCommand command =
                new InspectionRulePublicationTransactionService.PublishCommand(
                        TENANT_ID, TARGET_ID, 3, null, "权威分类", "权威严重度",
                        new LinkedHashMap<>(Map.of("A", "权威A", "B", "权威B")), ACTOR_ID, PUBLISHED_AT);

        InspectionRulePublicationTransactionService.PublishResult result = service.publishVerified(command);

        assertEquals(null, result.disabledRevisionId());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper).publishDraftIfMatch(any());
    }

    @Test
    void shouldRejectSnapshotCodeMismatchBeforeLifecycleWrites() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", CURRENT_ID, 1, 4));
        when(productTypeMapper.selectListByRevisionIds(any())).thenReturn(List.of(
                productType("OLD-A", "A"), productType("OLD-B", "B")));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishVerified(command(Map.of("A", "权威A"))));

        assertEquals(1_013_002_006, failure.getCode());
        verify(productTypeMapper, never()).updateNameSnapshot(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    @Test
    void shouldRejectWhenCurrentPublishedRevisionChangedAfterVerification() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 3));
        when(revisionMapper.selectPublicationLockForUpdate(any())).thenReturn(
                new InspectionRulePublicationLockProjection(
                        RULE_ID, 0, TARGET_ID, 3, "DRAFT", 99L, 2, 1));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishVerified(command(Map.of("A", "权威A", "B", "权威B"))));

        assertEquals(1_013_002_007, failure.getCode());
        verify(productTypeMapper, never()).selectListByRevisionIds(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    @Test
    void shouldRejectStaleVersionBeforeSnapshotOrLifecycleWrites() {
        when(revisionMapper.selectById(TARGET_ID)).thenReturn(revision("DRAFT", 4));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.publishVerified(command(Map.of("A", "权威A", "B", "权威B"))));

        assertEquals(1_013_002_007, failure.getCode());
        verify(revisionMapper, never()).selectPublicationLockForUpdate(any());
        verify(productTypeMapper, never()).updateNameSnapshot(any());
        verify(revisionMapper, never()).disablePublishedIfMatch(any());
        verify(revisionMapper, never()).publishDraftIfMatch(any());
    }

    private static InspectionRulePublicationTransactionService.PublishCommand command(
            Map<String, String> productTypeNames) {
        return new InspectionRulePublicationTransactionService.PublishCommand(
                TENANT_ID,
                TARGET_ID,
                3,
                CURRENT_ID,
                "权威分类",
                "权威严重度",
                new LinkedHashMap<>(productTypeNames),
                ACTOR_ID,
                PUBLISHED_AT);
    }

    private static InspectionRuleRevisionDO revision(String status, int version) {
        InspectionRuleRevisionDO revision = new InspectionRuleRevisionDO();
        revision.setId(TARGET_ID);
        revision.setTenantId(TENANT_ID);
        revision.setRuleId(RULE_ID);
        revision.setStatusCode(status);
        revision.setVersion(version);
        return revision;
    }

    private static InspectionRuleProductTypeRevisionDO productType(String name, String code) {
        InspectionRuleProductTypeRevisionDO productType = new InspectionRuleProductTypeRevisionDO();
        productType.setTenantId(TENANT_ID);
        productType.setRevisionId(TARGET_ID);
        productType.setProductTypeCode(code);
        productType.setProductTypeNameSnapshot(name);
        return productType;
    }
}
