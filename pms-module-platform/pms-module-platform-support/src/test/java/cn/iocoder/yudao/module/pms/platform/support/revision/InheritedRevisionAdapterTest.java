package cn.iocoder.yudao.module.pms.platform.support.revision;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.MutableEntityRevision;
import cn.iocoder.yudao.module.pms.platform.api.entity.RevisionRef;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 继承式修订适配器契约：草稿复制与元数据、保存只开放目录可写字段、
 * 冻结状态保护、生效基线校验与生效回写。Mapper 以受控桩承载，乐观锁条件由插件负责。
 */
class InheritedRevisionAdapterTest {

    /** 修订行版本桩：记录适配器写入的元数据与内容。 */
    static class DemoRevisionRow extends DemoRow implements MutableEntityRevision {
        private Long entityId;
        private Integer revisionNo;
        private Long sourceRevisionId;
        private Long baseEffectiveRevisionId;
        private Integer baseEntityVersion;
        private String changeReason;
        private Long frozenBy;
        private LocalDateTime frozenAt;
        private String stateText;
        private Boolean effective;

        @Override
        public Long getEntityId() {
            return entityId;
        }

        @Override
        public void setEntityId(Long entityId) {
            this.entityId = entityId;
        }

        @Override
        public Integer getRevisionNo() {
            return revisionNo;
        }

        @Override
        public void setRevisionNo(Integer revisionNo) {
            this.revisionNo = revisionNo;
        }

        @Override
        public Long getSourceRevisionId() {
            return sourceRevisionId;
        }

        @Override
        public void setSourceRevisionId(Long sourceRevisionId) {
            this.sourceRevisionId = sourceRevisionId;
        }

        @Override
        public Long getBaseEffectiveRevisionId() {
            return baseEffectiveRevisionId;
        }

        @Override
        public void setBaseEffectiveRevisionId(Long baseEffectiveRevisionId) {
            this.baseEffectiveRevisionId = baseEffectiveRevisionId;
        }

        @Override
        public Integer getBaseEntityVersion() {
            return baseEntityVersion;
        }

        @Override
        public void setBaseEntityVersion(Integer baseEntityVersion) {
            this.baseEntityVersion = baseEntityVersion;
        }

        @Override
        public String getChangeReason() {
            return changeReason;
        }

        @Override
        public void setChangeReason(String changeReason) {
            this.changeReason = changeReason;
        }

        @Override
        public Long getFrozenBy() {
            return frozenBy;
        }

        @Override
        public void setFrozenBy(Long frozenBy) {
            this.frozenBy = frozenBy;
        }

        @Override
        public LocalDateTime getFrozenAt() {
            return frozenAt;
        }

        @Override
        public void setFrozenAt(LocalDateTime frozenAt) {
            this.frozenAt = frozenAt;
        }

        @Override
        public EntityVersionProvider.Revision.State revisionState() {
            return stateText == null ? null : EntityVersionProvider.Revision.State.valueOf(stateText);
        }

        @Override
        public void setRevisionState(EntityVersionProvider.Revision.State state) {
            this.stateText = state == null ? null : state.name();
        }

        @Override
        public boolean effective() {
            return Boolean.TRUE.equals(effective);
        }

        @Override
        public void setEffective(boolean effective) {
            this.effective = effective;
        }

        @Override
        public EntityRef entityRef() {
            return new EntityRef(getTenantId(), "demo", "doc", entityId);
        }
    }

    static class DemoRow extends BaseBusinessEntity {
        private String title;
        private Integer level;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getLevel() {
            return level;
        }

        public void setLevel(Integer level) {
            this.level = level;
        }
    }

    /** 仅用于解析 Mapper 泛型的桩接口（行为由 Mockito 桩承载）。 */
    interface RevisionMapperSpec extends BaseMapper<DemoRevisionRow> {
    }

    interface BusinessMapperSpec extends BaseMapper<DemoRow> {
    }

    private static final Long TENANT = 1L;
    private static final EntityActor ACTOR = new EntityActor(TENANT, 7L, null);
    private static final Long ENTITY_ID = 5L;

    private BaseMapper<BusinessMapperSpec> businessMapperHolder;
    private DemoRow businessRow;
    private BaseMapper<RevisionMapperSpec> revisionMapperHolder;
    private BaseMapper revisionMapper;
    private DemoRevisionRow revisionRow;
    private InheritedRevisionAdapter adapter;
    private EntityRef entityRef;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        businessRow = new DemoRow();
        businessRow.setId(ENTITY_ID);
        businessRow.setTenantId(TENANT);
        businessRow.setVersion(3L);
        businessRow.setTitle("初始标题");
        businessRow.setLevel(2);

        BaseMapper businessMapper = mock(BusinessMapperSpec.class);
        when(businessMapper.selectById(ENTITY_ID)).thenReturn(businessRow);
        when(businessMapper.update(any(), any())).thenAnswer(inv -> {
            DemoRow replacement = inv.getArgument(0);
            businessRow.setTitle(replacement.getTitle());
            businessRow.setLevel(replacement.getLevel());
            businessRow.setVersion(businessRow.getVersion() + 1);
            return 1;
        });

        revisionRow = null;
        revisionMapper = mock(RevisionMapperSpec.class);
        when(revisionMapper.insert(any(DemoRevisionRow.class))).thenAnswer(inv -> {
            DemoRevisionRow draft = inv.getArgument(0);
            draft.setId(99L);
            revisionRow = draft;
            return 1;
        });
        when(revisionMapper.selectById(anyLong())).thenAnswer(inv -> revisionRow);
        when(revisionMapper.selectList(any())).thenReturn(List.of());
        when(revisionMapper.updateById(any(DemoRevisionRow.class))).thenReturn(1);
        when(revisionMapper.update(any(), any())).thenReturn(1);
        when(revisionMapper.delete(any())).thenReturn(1);

        var descriptor = new BusinessModelDescriptor("demo", "doc", "DEMO_DOC", 1,
                BusinessModelKind.AGGREGATE_ROOT, "演示文档", "demo:doc:manage",
                List.of(new BusinessFieldDescriptor("title", "标题", EntityField.Type.TEXT, true, true, true, null),
                        new BusinessFieldDescriptor("level", "级别", EntityField.Type.NUMBER, false, true, true, null)),
                List.of(),
                List.of(new BusinessOperationDescriptor("save", 1, "保存",
                        BusinessOperationDescriptor.StandardOperationKind.UPDATE)),
                List.of(new BusinessCapabilityBinding(BusinessCapabilityType.CONTENT_HISTORY, null, true)),
                "demo_doc");
        var declaration = new BusinessModelDeclaration(descriptor, DemoRow.class, businessMapper, revisionMapper);
        adapter = new InheritedRevisionAdapter(declaration, mock(BusinessAccessGuard.class), null, null);
        entityRef = new EntityRef(TENANT, "demo", "doc", ENTITY_ID);
        assertTrue(InheritedRevisionAdapter.supports(declaration));
    }

    @Test
    void createDraftCopiesBusinessFieldsAndMetadata() {
        var revision = adapter.createDraft(entityRef, null, "首次修订", ACTOR);
        assertEquals(1, revision.revisionNo());
        assertEquals(EntityVersionProvider.Revision.State.DRAFT, revision.state());
        assertEquals(3, revision.baseEntityVersion());
        assertFalse(revision.effective());
        assertEquals("初始标题", revisionRow.getTitle());
        assertEquals(2, revisionRow.getLevel());
        assertEquals(ENTITY_ID, revisionRow.getEntityId());
    }

    @Test
    void saveRejectsFieldsOutsideWritableDirectory() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        var ex = assertThrows(BusinessContractException.class,
                () -> adapter.save(ref, 0, Map.of("ghost", "x"), ACTOR));
        assertEquals("FIELD_NOT_WRITABLE", ex.getErrorCode());
    }

    @Test
    void saveAndFreezeOnlyOnDraft() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        var saved = adapter.save(ref, 0, Map.of("title", "草稿标题"), ACTOR);
        assertEquals(1, saved.version());
        assertEquals("草稿标题", revisionRow.getTitle());
        var frozen = adapter.freeze(ref, 1, ACTOR);
        assertEquals(EntityVersionProvider.Revision.State.FROZEN, frozen.state());
        assertEquals(7L, frozen.frozenBy());
        var ex = assertThrows(BusinessContractException.class,
                () -> adapter.save(ref, 2, Map.of("title", "再改"), ACTOR));
        assertEquals("ENTITY_REVISION_MISMATCH", ex.getErrorCode());
    }

    @Test
    void staleConcurrencyBasisRejectedOnSave() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        var ex = assertThrows(BusinessContractException.class,
                () -> adapter.save(ref, 5, Map.of("title", "x"), ACTOR));
        assertEquals("CONCURRENCY_CONFLICT", ex.getErrorCode());
    }

    @Test
    void activateRejectsChangedBaseline() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        adapter.save(ref, 0, Map.of(), ACTOR);
        adapter.freeze(ref, 1, ACTOR);
        businessRow.setVersion(9L);
        var ex = assertThrows(BusinessContractException.class, () -> adapter.activate(ref, 2, ACTOR));
        assertEquals("REVISION_BASE_CHANGED", ex.getErrorCode());
    }

    @Test
    void activateCopiesFrozenContentToCurrentRow() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        adapter.save(ref, 0, Map.of("title", "生效标题"), ACTOR);
        adapter.freeze(ref, 1, ACTOR);
        var activated = adapter.activate(ref, 2, ACTOR);
        assertTrue(activated.effective());
        assertEquals("生效标题", businessRow.getTitle());
        assertEquals(4L, businessRow.getVersion());
    }

    @Test
    void unknownRevisionIdentityRejected() {
        assertNull(adapter.inspect(new RevisionRef(entityRef, 404L), ACTOR));
    }

    @Test
    void discardRejectsFrozenRevision() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        adapter.save(ref, 0, Map.of(), ACTOR);
        adapter.freeze(ref, 1, ACTOR);
        var ex = assertThrows(BusinessContractException.class, () -> adapter.discard(ref, ACTOR));
        assertEquals("ENTITY_REVISION_MISMATCH", ex.getErrorCode());
    }

    @Test
    void discardVacatesRevisionNoAndRemovesDraft() {
        adapter.createDraft(entityRef, null, "修订", ACTOR);
        var ref = new RevisionRef(entityRef, 99L);
        adapter.discard(ref, ACTOR);
        // 先占位改写修订号，再逻辑删除腾出唯一键。
        verify(revisionMapper).update(any(), any());
        verify(revisionMapper).delete(any());
    }
}
