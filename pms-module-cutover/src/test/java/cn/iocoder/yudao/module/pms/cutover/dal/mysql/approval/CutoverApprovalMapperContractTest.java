package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeLockQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeStatusUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationClaimQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalReassignmentPageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTodoPageQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationClaimQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalTaskQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceReassignmentUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceStateUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverApprovalMapperContractTest {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-01T10:00:00");

    @Test
    void parsesEveryLockPageCasAndClaimBinding() throws IOException {
        Configuration configuration = configuration();
        assertBindings(configuration, CutoverApprovalInstanceMapper.class, "selectByTaskAndPlanForUpdate",
                new ApprovalInstanceLockQuery(1L, null, 2L, 3L));
        assertBindings(configuration, CutoverApprovalInstanceMapper.class, "selectByIdForUpdate",
                new ApprovalInstanceLockQuery(1L, 4L, null, null));
        assertBindings(configuration, CutoverApprovalInstanceMapper.class, "selectCurrentByTask",
                new ApprovalTaskQuery(1L, 10L));
        assertBindings(configuration, CutoverApprovalInstanceMapper.class, "updateAfterReassignmentIfMatch",
                new ApprovalInstanceReassignmentUpdate(1L, 4L, 0, null, "9", NOW));
        assertBindings(configuration, CutoverApprovalInstanceMapper.class, "updateStateIfMatch",
                new ApprovalInstanceStateUpdate(1L, 4L, 0, "PENDING", 2, null, null,
                        null, null, "9", NOW));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "selectByInstanceAndNodeForUpdate",
                new ApprovalNodeLockQuery(1L, 4L, 2));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "selectTodoPage",
                new ApprovalTodoPageQuery(1L, 9L, 0, 20));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "selectReassignmentPage",
                new ApprovalReassignmentPageQuery(1L, 0, 20));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "selectTodoProjectionPage",
                new ApprovalTodoPageQuery(1L, 9L, 0, 20));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "countTodos",
                new ApprovalTodoPageQuery(1L, 9L, 0, 20));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "selectReassignmentProjectionPage",
                new ApprovalReassignmentPageQuery(1L, 0, 20));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "countReassignmentCandidates",
                new ApprovalReassignmentPageQuery(1L, 0, 20));
        assertBindings(configuration, CutoverApprovalNodeMapper.class, "updateStatusIfMatch",
                new ApprovalNodeStatusUpdate(1L, 5L, 0, "PENDING", "APPROVED", 9L,
                        "{}", 11L, "CONFIRMED", null, "approved", NOW, "9", NOW));
        assertBindings(configuration, CutoverApprovalReassignmentMapper.class, "selectMaxReassignmentNo",
                new ApprovalNodeLockQuery(1L, 4L, 2));
        assertBindings(configuration, CutoverApprovalNotificationMapper.class,
                "selectDueForUpdateSkipLocked", new ApprovalNotificationClaimQuery(1L, NOW, 20));
        assertBindings(configuration, CutoverApprovalNotificationMapper.class, "updateDeliveryIfMatch",
                new ApprovalNotificationDeliveryUpdate(1L, 6L, 0, "PENDING", "SENT",
                        7L, 0, null, null, NOW, "9", NOW));
        assertBindings(configuration, CutoverApprovalNotificationMapper.class,
                "selectExternalDueForUpdateSkipLocked", new ExternalApprovalNotificationClaimQuery(1L, NOW, 20));
        assertBindings(configuration, CutoverApprovalNotificationMapper.class, "updateExternalDeliveryIfMatch",
                new ExternalApprovalNotificationDeliveryUpdate(1L, 6L, "SMS", 0, "PENDING", "ACCEPTED",
                        "provider-1", 0, null, null, NOW, "9", NOW));
    }

    @Test
    void keepsTenantDeleteLocksAndStableOrderingInXml() throws IOException {
        String instance = read("CutoverApprovalInstanceMapper.xml");
        String node = read("CutoverApprovalNodeMapper.xml");
        String reassignment = read("CutoverApprovalReassignmentMapper.xml");
        String notification = read("CutoverApprovalNotificationMapper.xml");
        assertThat(instance).contains("tenant_id = #{query.tenantId}", "deleted = b'0'", "FOR UPDATE")
                .doesNotContain("${");
        assertThat(node).contains("n.tenant_id = #{query.tenantId}", "n.deleted = b'0'",
                        "FOR UPDATE", "ORDER BY i.create_time ASC, i.id ASC, n.id ASC")
                .doesNotContain("${");
        assertThat(reassignment).contains("tenant_id = #{query.tenantId}", "deleted = b'0'")
                .doesNotContain("${");
        assertThat(notification).contains("tenant_id = #{query.tenantId}", "deleted = b'0'",
                        "channel_code = 'IN_PLATFORM'", "channel_code IN ('SMS','EMAIL','DINGTALK')",
                        "FOR UPDATE SKIP LOCKED", "ORDER BY COALESCE(next_retry_at, create_time) ASC, id ASC",
                        "channel_code = #{query.channelCode}")
                .doesNotContain("${");
    }

    @Test
    void separatesPlatformAndExternalDeliveryCasByChannel() throws IOException {
        Configuration configuration = configuration();
        String platformSql = boundSql(configuration, CutoverApprovalNotificationMapper.class,
                "updateDeliveryIfMatch", new ApprovalNotificationDeliveryUpdate(1L, 6L, 0,
                        "PENDING", "SENT", 7L, 0, null, null, NOW, "9", NOW)).getSql();
        String externalSql = boundSql(configuration, CutoverApprovalNotificationMapper.class,
                "updateExternalDeliveryIfMatch", new ExternalApprovalNotificationDeliveryUpdate(1L, 6L,
                        "SMS", 0, "PENDING", "ACCEPTED", "provider-1", 0,
                        null, null, NOW, "9", NOW)).getSql();

        assertThat(platformSql).contains("channel_code = 'IN_PLATFORM'");
        assertThat(externalSql).contains("channel_code = ?", "channel_code IN ('SMS','EMAIL','DINGTALK')");
    }

    private static Configuration configuration() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, "CutoverApprovalInstanceMapper.xml");
        parse(configuration, "CutoverApprovalNodeMapper.xml");
        parse(configuration, "CutoverApprovalReassignmentMapper.xml");
        parse(configuration, "CutoverApprovalNotificationMapper.xml");
        return configuration;
    }

    private static void parse(Configuration configuration, String file) throws IOException {
        Path path = Path.of("src/main/resources/mapper/approval", file);
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of("src/main/resources/mapper/approval", file));
    }

    private static void assertBindings(Configuration configuration, Class<?> mapper, String method, Object query) {
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = boundSql(configuration, mapper, method, query);
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
        boundSql.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
    }

    private static BoundSql boundSql(Configuration configuration, Class<?> mapper, String method, Object query) {
        return configuration.getMappedStatement(mapper.getName() + "." + method)
                .getBoundSql(Map.of("query", query));
    }
}
