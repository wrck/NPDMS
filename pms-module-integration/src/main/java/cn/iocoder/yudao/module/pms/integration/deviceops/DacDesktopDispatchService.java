package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsDesktopApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsDispatchDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops.DeviceOpsDispatchMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionDesktopSourceApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Task capabilities authorize one local execution; there is no client registration or shared device password. */
@Service
@ConditionalOnProperty(prefix="pms.integration.device-ops",name="enabled",havingValue="true")
public class DacDesktopDispatchService implements DeviceOpsDesktopApi {
    private static final Set<String> OPERATIONS = Set.of("EXECUTE", "TASK_READ", "CONNECTIONS_READ", "CONNECTION_SAVE");
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED", "SECURITY_EXCEPTION");
    private static final Set<String> OBSERVABLE = Set.of("QUEUED", "CONNECTING", "EXECUTING", "PARSING", "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "TIMED_OUT", "CANCELLED", "SECURITY_EXCEPTION");
    private final DacDeviceOpsGatewayProperties properties;
    private final DeviceOpsDispatchMapper dispatches;
    private final CollectionDesktopSourceApi sources;
    private final byte[] callbackKey;

    public DacDesktopDispatchService(DacDeviceOpsGatewayProperties properties, DeviceOpsDispatchMapper dispatches,
            CollectionDesktopSourceApi sources, @Value("${pms.integration.device-ops.callback-signing-key:}") String callbackKey) {
        this.properties = properties;
        this.dispatches = dispatches;
        this.sources = sources;
        this.callbackKey = callbackKey.getBytes(StandardCharsets.UTF_8);
        if (this.callbackKey.length < 32) throw new IllegalArgumentException("DAC_CALLBACK_SIGNING_KEY_REQUIRED");
        if (properties.getRequestSigningKey() == null || properties.getRequestSigningKey().getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("DAC_REQUEST_SIGNING_KEY_REQUIRED");
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public Ticket register(Registration command) {
        requireTenant(command.tenantId());
        var source = sources.snapshot(new CollectionDesktopSourceApi.TaskKey(command.tenantId(), command.platformTaskId()));
        if (!Objects.equals(source.actorId(), command.actorId()) || !Objects.equals(source.task().projectId(), command.projectId())) reject();
        var row = dispatches.selectByTenantAndPlatformTaskId(command.tenantId(), command.platformTaskId());
        if (row == null) {
            row = new DeviceOpsDispatchDO();
            row.setTenantId(command.tenantId());
            row.setPlatformTaskId(command.platformTaskId());
            row.setIdempotencyKey(command.platformTaskId());
            row.setNamespace(properties.getNamespace() + "-" + command.tenantId());
            row.setTraceId(command.platformTaskId());
            row.setDispatchMode("DESKTOP");
            row.setActorId(command.actorId());
            row.setProjectId(command.projectId());
            row.setDesktopClaimed(false);
            row.setCancelRequested(false);
            row.setRedeliveryRequested(false);
            try { dispatches.insert(row); }
            catch (org.springframework.dao.DuplicateKeyException duplicate) {
                row = dispatches.selectByTenantAndPlatformTaskId(command.tenantId(), command.platformTaskId());
                if (row == null) throw duplicate;
            }
        }
        if (!isDesktop(row) || !Objects.equals(row.getActorId(), command.actorId())
                || !Objects.equals(row.getProjectId(), command.projectId())) conflict();
        return issue(new Claims(command.tenantId(), command.actorId(), command.projectId(), "EXECUTE",
                command.platformTaskId(), Instant.now().getEpochSecond()));
    }

    @Override public Ticket access(Access command) {
        requireTenant(command.tenantId());
        if (!Set.of("TASK_READ", "CONNECTIONS_READ", "CONNECTION_SAVE").contains(command.operation())
                || command.actorId() == null || command.projectId() == null) reject();
        if ("TASK_READ".equals(command.operation())) {
            var row = required(command.platformTaskId(), false);
            if (!Objects.equals(row.getProjectId(), command.projectId())) reject();
        } else if (command.platformTaskId() != null) reject();
        return issue(new Claims(command.tenantId(), command.actorId(), command.projectId(), command.operation(),
                command.platformTaskId(), Instant.now().getEpochSecond()));
    }

    public Grant resolve(String ticket) {
        Claims claims = verify(ticket);
        requireTenant(claims.tenantId());
        String namespace = properties.getNamespace() + "-" + claims.tenantId();
        Map<String, Object> submission = null;
        ExpectedConnection connection = null;
        String taskToken = null;
        if (Set.of("EXECUTE", "TASK_READ").contains(claims.operation())) {
            var row = required(claims.platformTaskId(), false);
            if (!Objects.equals(row.getProjectId(), claims.projectId()) || !namespace.equals(row.getNamespace())) reject();
            if ("EXECUTE".equals(claims.operation())) {
                if (!Objects.equals(row.getActorId(), claims.actorId()) || Boolean.TRUE.equals(row.getCancelRequested())) conflict();
                var snapshot = sources.snapshot(new CollectionDesktopSourceApi.TaskKey(claims.tenantId(), claims.platformTaskId()));
                var task = snapshot.task();
                if (!Objects.equals(snapshot.actorId(), claims.actorId())) reject();
                var commands = snapshot.commandText().isEmpty() ? List.<String>of() : Arrays.asList(snapshot.commandText().split("\n", -1));
                var command = new DeviceOpsDispatchCommand(task.platformTaskId(), Objects.toString(task.batchId(), null), claims.tenantId(),
                        task.projectId(), task.deviceId(), task.deviceName(), task.host(), task.port(), task.protocol(),
                        task.templateId(), task.templateVersion(), task.templateHash(), commands, "LOCAL_TEMPORARY", null,
                        snapshot.username(), null, null, task.platformTaskId(), task.sourceContext(), task.sourceObjectType(), null, null,
                        snapshot.executionProfile() == null ? null : new DeviceOpsExecutionOptions(snapshot.executionProfile().purpose()));
                submission = DacCollectionPayloadFactory.build(new DacCollectionPayloadFactory.Input(command, properties, namespace, null));
                connection = new ExpectedConnection(task.host(), task.port(), DacCollectionPayloadFactory.protocol(task.protocol()),
                        snapshot.username(), snapshot.localConnectionId(), snapshot.localConnectionVersion());
                taskToken = taskToken(task.platformTaskId());
            }
        }
        return new Grant(claims.tenantId(), claims.actorId(), claims.projectId(), namespace, claims.operation(),
                claims.platformTaskId(), submission, connection, taskToken);
    }

    @Transactional(rollbackFor=Exception.class)
    public void claim(Claim command) {
        var authorized = authenticate(command.authorization(), false);
        sources.snapshot(new CollectionDesktopSourceApi.TaskKey(authorized.getTenantId(), authorized.getPlatformTaskId()));
        var row = authenticate(command.authorization(), true);
        if (command.executionId() == null || !command.executionId().matches("[a-f0-9-]{36}")) reject();
        if (Boolean.TRUE.equals(row.getCancelRequested()) || "CANCELLED_BEFORE_DISPATCH".equals(row.getExternalStatus())) conflict();
        if (row.getDesktopClaimId() != null) {
            if (!command.executionId().equals(row.getDesktopClaimId())) conflict();
            return;
        }
        row.setDesktopClaimed(true);
        row.setDesktopClaimId(command.executionId());
        dispatches.updateById(row);
    }

    @Transactional(rollbackFor=Exception.class)
    public Control observe(Observation observation) {
        var row = authenticate(observation.authorization(), true);
        if (!Objects.equals(row.getDesktopClaimId(), observation.executionId()) || row.getDesktopClaimId() == null
                || observation.collectionId() == null || observation.collectionId().isBlank() || observation.collectionId().length() > 128
                || !OBSERVABLE.contains(observation.status())) reject();
        if (row.getCollectionId() != null && !row.getCollectionId().equals(observation.collectionId())) conflict();
        if (TERMINAL.contains(Objects.toString(row.getExternalStatus(), "")) && !row.getExternalStatus().equals(observation.status())) conflict();
        sources.accepted(new CollectionDesktopSourceApi.Acceptance(
                new CollectionDesktopSourceApi.TaskKey(row.getTenantId(), row.getPlatformTaskId()), observation.collectionId(), observation.status()));
        row.setCollectionId(observation.collectionId());
        row.setExternalStatus(observation.status());
        if (observation.redeliveryApplied()) row.setRedeliveryRequested(false);
        dispatches.updateById(row);
        return new Control(Boolean.TRUE.equals(row.getCancelRequested()), Boolean.TRUE.equals(row.getRedeliveryRequested()));
    }

    public void authenticateCallback(CallbackAuthorization command) {
        var row = authenticate(command.authorization(), false);
        if (row.getCollectionId() == null || !row.getCollectionId().equals(command.collectionId())) conflict();
    }

    public Control control(Claim command) {
        var row = authenticate(command.authorization(), false);
        if (row.getDesktopClaimId() == null || !row.getDesktopClaimId().equals(command.executionId())) conflict();
        return new Control(Boolean.TRUE.equals(row.getCancelRequested()), Boolean.TRUE.equals(row.getRedeliveryRequested()));
    }

    @Transactional(rollbackFor=Exception.class)
    public void cancelledBeforeSubmission(Claim command) {
        var row = authenticate(command.authorization(), true);
        if (!Objects.equals(row.getDesktopClaimId(), command.executionId()) || row.getDesktopClaimId() == null
                || !Boolean.TRUE.equals(row.getCancelRequested()) || row.getCollectionId() != null) conflict();
        // A durable local cancellation fence is the evidence; PLT still applies its normal reconciliation state machine.
        row.setExternalStatus("CANCELLED_BEFORE_DISPATCH");
        dispatches.updateById(row);
    }

    @Transactional(rollbackFor=Exception.class)
    public void cancel(String taskId) {
        var row = required(taskId, true);
        row.setCancelRequested(true);
        if (!Boolean.TRUE.equals(row.getDesktopClaimed()) && row.getCollectionId() == null) row.setExternalStatus("CANCELLED_BEFORE_DISPATCH");
        dispatches.updateById(row);
    }

    @Transactional(rollbackFor=Exception.class)
    public void redeliver(String taskId) {
        var row = required(taskId, true);
        row.setRedeliveryRequested(true);
        dispatches.updateById(row);
    }

    public DeviceOpsTaskSnapshot query(DeviceOpsDispatchDO row) {
        if ("CANCELLED_BEFORE_DISPATCH".equals(row.getExternalStatus()))
            return new DeviceOpsTaskSnapshot(row.getPlatformTaskId(), null, "CANCELLED", "CANCELLED_BEFORE_DISPATCH", null, null, null, null);
        return new DeviceOpsTaskSnapshot(row.getPlatformTaskId(), row.getCollectionId(),
                Objects.toString(row.getExternalStatus(), "UNKNOWN"), row.getCollectionId() == null ? "NOT_DISPATCHED" : null,
                null, null, null, null);
    }

    static boolean isDesktop(DeviceOpsDispatchDO row) { return row != null && "DESKTOP".equals(row.getDispatchMode()); }
    private DeviceOpsDispatchDO required(String taskId, boolean lock) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var row = lock ? dispatches.lockByTenantAndPlatformTaskId(tenant, taskId) : dispatches.selectByTenantAndPlatformTaskId(tenant, taskId);
        if (!isDesktop(row)) reject();
        return row;
    }
    private DeviceOpsDispatchDO authenticate(Authorization authorization, boolean lock) {
        var row = required(authorization.platformTaskId(), lock);
        if (authorization.token() == null || !MessageDigest.isEqual(taskToken(row.getPlatformTaskId()).getBytes(StandardCharsets.US_ASCII),
                authorization.token().getBytes(StandardCharsets.US_ASCII))) reject();
        return row;
    }
    private String taskToken(String taskId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(callbackKey,
                "npdms-dac-desktop-task-v1\n" + TenantContextHolder.getRequiredTenantId() + "\n" + taskId));
    }
    private Ticket issue(Claims claims) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(JsonUtils.toJsonString(claims).getBytes(StandardCharsets.UTF_8));
        return new Ticket(payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ticketMac(payload)), claims.tenantId());
    }
    private Claims verify(String value) {
        try {
            if (value == null || value.length() > 4096) reject();
            var parts = value.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(ticketMac(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) reject();
            Claims claims = JsonUtils.parseObject(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8), Claims.class);
            long now = Instant.now().getEpochSecond();
            if (claims == null || claims.actorId() == null || claims.projectId() == null || !OPERATIONS.contains(claims.operation())
                    || claims.issuedAt() > now || claims.issuedAt() < now - 60) reject();
            return claims;
        } catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "DAC_DESKTOP_TICKET_REJECTED"); }
    }
    private byte[] ticketMac(String payload) {
        return hmac(properties.getRequestSigningKey().getBytes(StandardCharsets.UTF_8), "npdms-dac-desktop-ticket-v1\n" + payload);
    }
    private static byte[] hmac(byte[] key, String value) {
        try { var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("DAC_DESKTOP_SIGNING_FAILED"); }
    }
    private static void requireTenant(Long tenant) { if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId())) reject(); }
    private static void reject() { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "DAC_DESKTOP_AUTHORIZATION_REJECTED"); }
    private static void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT, "DAC_DESKTOP_TASK_BINDING_CONFLICT"); }

    record Claims(Long tenantId, Long actorId, String projectId, String operation, String platformTaskId, long issuedAt) { }
    public record Grant(Long tenantId, Long actorId, String projectId, String namespace, String operation, String platformTaskId,
                        Map<String, Object> submission, ExpectedConnection connection, String taskToken) {
        @Override public String toString() { return "DesktopGrant[redacted]"; }
    }
    public record ExpectedConnection(String host, Integer port, String protocol, String username, String savedConnectionId, Long savedConnectionVersion) { }
    public record Authorization(String platformTaskId, String token) { @Override public String toString() { return "DesktopAuthorization[redacted]"; } }
    public record Claim(Authorization authorization, String executionId) { }
    public record Observation(Authorization authorization, String executionId, String collectionId, String status, boolean redeliveryApplied) { }
    public record CallbackAuthorization(Authorization authorization, String collectionId) { }
    public record Control(boolean cancelRequested, boolean redeliveryRequested) { }
}
