package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverExternalNotificationRequestFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverApprovalExternalNotificationCreationTest {

    @Test
    void createsThreePendingChannelRequestsForTheCommittedNodeVersion() {
        CutoverApprovalInstanceDO instance = new CutoverApprovalInstanceDO();
        instance.setId(100L);
        instance.setTenantId(1L);
        CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
        node.setId(101L);
        node.setNodeNo(2);
        node.setCurrentApproverUserId(22L);
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 9, 0);

        var rows = new CutoverExternalNotificationRequestFactory()
                .createForActivatedNode(instance, node, 3, 99L, now);

        assertThat(rows).extracting(value -> value.getChannelCode())
                .containsExactly("SMS", "EMAIL", "DINGTALK");
        assertThat(rows).extracting(value -> value.getDeliveryKey()).containsExactly(
                "CUT_APPROVAL_EXT:100:2:3:SMS",
                "CUT_APPROVAL_EXT:100:2:3:EMAIL",
                "CUT_APPROVAL_EXT:100:2:3:DINGTALK");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getTenantId()).isEqualTo(1L);
            assertThat(row.getApprovalInstanceId()).isEqualTo(100L);
            assertThat(row.getApprovalNodeId()).isEqualTo(101L);
            assertThat(row.getRecipientUserId()).isEqualTo(22L);
            assertThat(row.getTemplateCode()).isEqualTo("CUT_APPROVAL_PENDING_V2");
            assertThat(row.getStatusCode()).isEqualTo("PENDING");
            assertThat(row.getRetryCount()).isZero();
            assertThat(row.getMessageId()).isNull();
            assertThat(row.getProviderReferenceId()).isNull();
            assertThat(row.getNextRetryAt()).isNull();
            assertThat(row.getLastAttemptAt()).isNull();
            assertThat(row.getSentAt()).isNull();
            assertThat(row.getCreateTime()).isEqualTo(now);
        });
    }
}
