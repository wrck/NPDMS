package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverRuntimePlanContentCodec;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CutoverPlanContactPolicyTest {
    final ProjectContactReferenceApi contacts = mock(ProjectContactReferenceApi.class);
    final CutoverPlanContactPolicy policy = new CutoverPlanContactPolicy(contacts,
            mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper.class),
            mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverPlanSupportContactChangeMapper.class),
            mock(CutoverPlanStorage.class), mock(OperationAuditApi.class));

    @Test void submitsOnlyTheCurrentProjectContactIdentityAndVersion() {
        when(contacts.lockAndInspect(any())).thenReturn(new ProjectContactReferenceApi.Reference(
                20L, 3, 30L, 99L, "张工", "13800000000", null, "网络部", "经理"));
        policy.validate(new cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi.Query(1L, 2L, 30L), content("张工", true), true);
        verify(contacts).lockAndInspect(new ProjectContactReferenceApi.Revalidation(1L, 2L, 30L, 20L, 3));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(new cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi.Query(1L, 2L, 30L), content("手填的其他人", true), true));
    }

    @Test void missingDraftContactCanBeSavedButCannotBeSubmitted() {
        policy.validate(new cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi.Query(1L, 2L, 30L), content("张工", false), false);
        assertThrows(IllegalArgumentException.class, () -> policy.validate(new cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi.Query(1L, 2L, 30L), content("张工", false), true));
        verifyNoInteractions(contacts);
    }

    @Test void staleOrDisabledOwnerFactIsNotReplacedWithTypedText() {
        when(contacts.lockAndInspect(any())).thenThrow(new IllegalArgumentException("联系记录版本已变化或停用"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(new cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi.Query(1L, 2L, 30L), content("张工", true), true));
    }

    @Test void referenceRejectsAdditionalOrMalformedFields() {
        assertThrows(IllegalArgumentException.class, () -> CutoverPlanContactPolicy.selection(JsonUtils.parseTree(
                "{\"contactId\":20,\"contactVersion\":3,\"name\":\"伪造\"}")));
        assertThrows(IllegalArgumentException.class, () -> CutoverPlanContactPolicy.selection(JsonUtils.parseTree(
                "{\"contactId\":20,\"contactVersion\":-1}")));
    }

    @Test void validatesEachCustomerRowInsteadOfReusingTheFirstSelection() {
        when(contacts.lockAndInspect(any())).thenAnswer(call -> {
            var reference = call.<ProjectContactReferenceApi.Revalidation>getArgument(0);
            if (reference.contactId().equals(20L)) return new ProjectContactReferenceApi.Reference(
                    20L, 3, 30L, 99L, "张工", "13800000000", null, null, null);
            return new ProjectContactReferenceApi.Reference(21L, 4, 30L, 100L, "李工", "13900000000", null, null, null);
        });
        var first = content("张工", true);
        var second = new CutoverRuntimePlanContentCodec.SupportArrangement(null, "CUSTOMER", "李工", "保障", "13900000000", 1,
                new CutoverPlanContactPolicy.Selection(21L, 4));
        var two = new CutoverRuntimePlanContentCodec.DecodedContent(first.rootSnapshot(), List.of(),
                List.of(first.supportArrangements().getFirst(), second), null, false);
        var query = new ProjectContactReferenceApi.Query(1L, 2L, 30L);
        policy.validate(query, two, true);
        verify(contacts).lockAndInspect(new ProjectContactReferenceApi.Revalidation(1L, 2L, 30L, 20L, 3));
        verify(contacts).lockAndInspect(new ProjectContactReferenceApi.Revalidation(1L, 2L, 30L, 21L, 4));
        when(contacts.lockAndInspect(new ProjectContactReferenceApi.Revalidation(1L, 2L, 30L, 21L, 4)))
                .thenThrow(new IllegalArgumentException("第二位联系人版本已变化"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(query, two, true));
    }

    @Test void blankPhoneRemainsADraftButCannotPassSubmission() {
        when(contacts.lockAndInspect(any())).thenReturn(new ProjectContactReferenceApi.Reference(
                20L, 3, 30L, 99L, "张工", null, null, null, null));
        var root = content("张工", true).rootSnapshot();
        var draft = new CutoverRuntimePlanContentCodec.DecodedContent(root, List.of(), List.of(
                new CutoverRuntimePlanContentCodec.SupportArrangement(null, "CUSTOMER", "张工", "", "", 0,
                        new CutoverPlanContactPolicy.Selection(20L, 3))), null, false);
        var query = new ProjectContactReferenceApi.Query(1L, 2L, 30L);
        assertDoesNotThrow(() -> policy.validate(query, draft, false));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(query, draft, true));
    }

    private CutoverRuntimePlanContentCodec.DecodedContent content(String name, boolean reference) {
        var root = JsonUtils.getObjectMapper().createObjectNode(); root.put("editMode", "ONLINE_TEMPLATE_STANDARD");
        return new CutoverRuntimePlanContentCodec.DecodedContent(root, List.of(), List.of(
                new CutoverRuntimePlanContentCodec.SupportArrangement(null, "CUSTOMER", name, "保障", "13800000000", 1L,
                        reference ? new CutoverPlanContactPolicy.Selection(20L, 3) : null)), null, false);
    }
}
