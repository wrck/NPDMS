package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.ExternalApprovalNotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverApprovalPositiveLoopMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverLeadTimeExternalNotificationPositiveLoopMySqlTest extends CutoverApprovalPositiveLoopMySqlTest {

    @Override
    void externalDueRowsAreClaimedOnceAcrossConcurrentWorkers() {
        // Covered by the base positive-loop suite; this class owns the F-CUT-008 closure scenarios only.
    }

    @Override
    void allGradesReachP6WithRealCutPlatformAndMysql(String grade, int expectedNodes) {
        // Covered by the base positive-loop suite; this class owns the F-CUT-008 closure scenarios only.
    }

    @Override
    void rejectedRevisionIsImmutableAndReplacementStartsNewApproval() {
        // Covered by the base positive-loop suite; this class owns the F-CUT-008 closure scenarios only.
    }

    @Override
    void fullFileUploadReachesP6WithoutTemplateChildrenInFrozenContent() {
        // Covered by the base positive-loop suite; this class owns the F-CUT-008 closure scenarios only.
    }

    @ParameterizedTest
    @CsvSource({
            "A,4,2026-09-04T10:00:00,3,false",
            "B,3,2026-09-03T10:00:00,2,true"
    })
    void closesLeadTimeAndControlledExternalNotificationLoop(
            String grade, int expectedNodes, LocalDateTime scheduledTime, int actualDays, boolean late) {
        SubmittedRoute route = submit(grade, "fcut008-" + grade, scheduledTime);
        String snapshotJson = jdbc.queryForObject("SELECT lead_time_snapshot FROM cut_approval_instance " +
                "WHERE tenant_id=? AND id=?", String.class, tenantId, route.approvalInstanceId());
        var snapshot = new CutoverLeadTimeSnapshotCodec().decode(snapshotJson);
        assertEquals(3, snapshot.requiredDays());
        assertEquals(actualDays, snapshot.actualNaturalDays());
        assertEquals(late, snapshot.lateSubmission());

        approveAll(route, expectedNodes);
        assertEquals(1, count("SELECT COUNT(*) FROM cut_task WHERE tenant_id=? AND id=? " +
                "AND current_stage='P6' AND task_status='CLOSURE_IN_PROGRESS' AND version=6", tenantId, taskId));
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM (SELECT approval_node_id " +
                "FROM cut_approval_notification WHERE tenant_id=? AND approval_instance_id=? " +
                "GROUP BY approval_node_id HAVING COUNT(*)=4 " +
                "AND SUM(channel_code='IN_PLATFORM')=1 " +
                "AND SUM(channel_code IN ('SMS','EMAIL','DINGTALK'))=3 " +
                "AND COUNT(DISTINCT correlation_id)=1) grouped_notifications",
                tenantId, route.approvalInstanceId()));

        ApprovalFacts frozenApproval = approvalFacts(route.approvalInstanceId());
        var stationDelivery = notificationService.deliverDue(tenantId, LocalDateTime.of(2026, 9, 2, 0, 0), 100);
        assertEquals(expectedNodes, stationDelivery.sent());

        externalNotificationPort.useControlledChannelResults();
        var first = externalNotificationService.deliverDue(tenantId, LocalDateTime.of(2026, 9, 2, 0, 0), 100);
        assertEquals(expectedNodes, first.accepted());
        assertEquals(expectedNodes, first.deliveryUnknown());
        assertEquals(expectedNodes, first.retryScheduled());
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND channel_code='EMAIL' AND status_code='PENDING_RETRY' " +
                "AND retry_count=1 AND next_retry_at='2026-09-02 00:01:00'", tenantId, route.approvalInstanceId()));

        var second = externalNotificationService.deliverDue(tenantId, LocalDateTime.of(2026, 9, 2, 0, 1), 100);
        assertEquals(expectedNodes, second.accepted());
        assertEquals(0, second.deliveryUnknown());
        assertEquals(0, second.retryScheduled());
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND channel_code='SMS' AND status_code='ACCEPTED'", tenantId,
                route.approvalInstanceId()));
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND channel_code='EMAIL' AND status_code='ACCEPTED' " +
                "AND retry_count=1", tenantId, route.approvalInstanceId()));
        assertEquals(expectedNodes, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND channel_code='DINGTALK' AND status_code='DELIVERY_UNKNOWN'", tenantId,
                route.approvalInstanceId()));
        assertEmailRetriesKeepFrozenIdentity(expectedNodes);
        assertEquals(frozenApproval, approvalFacts(route.approvalInstanceId()));
    }

    @ParameterizedTest
    @CsvSource({"C,2", "D,2"})
    void nonApplicableGradesStillCloseExternalNotificationLoopWithoutLeadTimeSnapshot(
            String grade, int expectedNodes) {
        SubmittedRoute route = submit(grade, "fcut008-no-lead-" + grade);
        assertNull(jdbc.queryForObject("SELECT lead_time_snapshot FROM cut_approval_instance " +
                "WHERE tenant_id=? AND id=?", String.class, tenantId, route.approvalInstanceId()));

        approveAll(route, expectedNodes);
        assertEquals(expectedNodes * 3, count("SELECT COUNT(*) FROM cut_approval_notification WHERE tenant_id=? " +
                "AND approval_instance_id=? AND channel_code IN ('SMS','EMAIL','DINGTALK') " +
                "AND status_code='PENDING'", tenantId, route.approvalInstanceId()));
        var delivered = externalNotificationService.deliverDue(tenantId,
                LocalDateTime.of(2026, 9, 2, 0, 0), 100);
        assertEquals(expectedNodes * 3, delivered.accepted());
        assertEquals(1, count("SELECT COUNT(*) FROM cut_task WHERE tenant_id=? AND id=? " +
                "AND current_stage='P6' AND task_status='CLOSURE_IN_PROGRESS'", tenantId, taskId));
    }

    private void assertEmailRetriesKeepFrozenIdentity(int expectedNodes) {
        Map<String, List<ExternalApprovalNotificationRequest>> emailRequests = externalNotificationPort.requests().stream()
                .filter(request -> "EMAIL".equals(request.channel()))
                .collect(java.util.stream.Collectors.groupingBy(ExternalApprovalNotificationRequest::deliveryKey));
        assertEquals(expectedNodes, emailRequests.size());
        emailRequests.values().forEach(requests -> {
            assertEquals(2, requests.size());
            assertEquals(requests.get(0).deliveryKey(), requests.get(1).deliveryKey());
            assertEquals(requests.get(0).correlationId(), requests.get(1).correlationId());
            assertEquals(requests.get(0).approvalInstanceId(), requests.get(1).approvalInstanceId());
            assertEquals(requests.get(0).nodeNo(), requests.get(1).nodeNo());
        });
    }

    private ApprovalFacts approvalFacts(long approvalInstanceId) {
        return new ApprovalFacts(
                jdbc.queryForList("SELECT status_code,current_node_no,decision_at,version,lead_time_snapshot " +
                        "FROM cut_approval_instance WHERE tenant_id=? AND id=?", tenantId, approvalInstanceId),
                jdbc.queryForList("SELECT node_no,node_code,status_code,current_approver_user_id,decision_at,version " +
                        "FROM cut_approval_node WHERE tenant_id=? AND approval_instance_id=? ORDER BY node_no",
                        tenantId, approvalInstanceId),
                jdbc.queryForList("SELECT approval_node_id,item_code,decision_code,unreasonable_reason " +
                        "FROM cut_approval_review_item WHERE tenant_id=? AND approval_instance_id=? " +
                        "ORDER BY approval_node_id,item_code", tenantId, approvalInstanceId),
                jdbc.queryForList("SELECT current_stage,task_status,version FROM cut_task WHERE tenant_id=? AND id=?",
                        tenantId, taskId),
                jdbc.queryForList("SELECT event_type,aggregate_type,aggregate_key,status FROM plt_outbox_event " +
                        "WHERE tenant_id=? AND event_type='CutoverApproved' ORDER BY id", tenantId));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private record ApprovalFacts(List<Map<String, Object>> root, List<Map<String, Object>> nodes,
                                 List<Map<String, Object>> reviews, List<Map<String, Object>> task,
                                 List<Map<String, Object>> approvedEvents) {
    }
}
