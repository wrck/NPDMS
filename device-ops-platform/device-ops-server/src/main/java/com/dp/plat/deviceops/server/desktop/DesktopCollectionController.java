package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.JdbcDesktopTaskStore;
import com.dp.plat.deviceops.adapter.persistence.jdbc.JdbcDesktopConnectionScopeStore;
import com.dp.plat.deviceops.adapter.web.*;
import com.dp.plat.deviceops.adapter.web.parser.CollectionSemanticResultController;
import com.dp.plat.deviceops.core.port.*;
import com.dp.plat.deviceops.core.service.SavedConnectionService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@Profile("desktop")
@RequestMapping("/api/v1/desktop")
public class DesktopCollectionController {
    private final GenericCollectionController collections;
    private final NpdmsCollectionController reads;
    private final SavedConnectionController connections;
    private final SavedConnectionStore saved;
    private final JdbcDesktopTaskStore tasks;
    private final DesktopServerClient server;
    private final Validator validator;
    private final JdbcDesktopConnectionScopeStore connectionScopes;
    private final ObjectMapper json = new ObjectMapper();

    public DesktopCollectionController(GenericCollectionController collections, NpdmsCollectionController reads,
            SavedConnectionController connections, SavedConnectionStore saved, JdbcDesktopTaskStore tasks,
            DesktopServerClient server, Validator validator, JdbcDesktopConnectionScopeStore connectionScopes) {
        this.collections = collections; this.reads = reads; this.connections = connections; this.saved = saved;
        this.tasks = tasks; this.server = server; this.validator = validator;
        this.connectionScopes = connectionScopes;
    }

    @PostMapping("/tasks/{taskId}/executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CollectionSubmissionCoordinator.Result execute(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http,
            @PathVariable("taskId") String taskId, @Valid @RequestBody Execute input) {
        GenericCollectionController.Request request = null;
        try {
            var grant = grant(http, "EXECUTE");
            requireTask(grant, taskId);
            if (grant.submission() == null || grant.connection() == null || grant.taskToken() == null) reject();
            Map<String, Object> body = new LinkedHashMap<>(grant.submission());
            body.put("callbackUrl", server.callbackUrl());
            CollectionSubmissionSnapshot.installAuthorized(new CollectionSubmissionSnapshot.AuthorizedSubmission(
                    body, "/api/v1/desktop/tasks/" + taskId + "/executions", taskId));
            @SuppressWarnings("unchecked")
            var connection = new LinkedHashMap<>((Map<String, Object>) body.get("connection"));
            var expected = grant.connection();
            if (expected.savedConnectionId() == null) {
                if (input.password() == null || input.password().length == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入设备密码");
                connection.put("password", input.password());
            } else {
                if (input.password() != null) reject();
                if (!connectionScopes.matching(scope(jwt, grant)).contains(expected.savedConnectionId())) reject();
                var stored = saved.find(jwt.getSubject(), grant.namespace(), expected.savedConnectionId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "本机保存的连接不存在"));
                var endpoint = stored.connection();
                if (!Objects.equals(expected.savedConnectionVersion(), stored.version()) || !expected.host().equals(endpoint.host())
                        || expected.port() != endpoint.port() || !expected.protocol().equals(endpoint.protocol().name())
                        || !expected.username().equals(endpoint.username())
                        || endpoint.authenticationType() != CommandExecutionPort.AuthenticationType.PASSWORD
                        || endpoint.executionMode() != CommandExecutionPort.ExecutionMode.SHELL) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "本机保存的连接已变化，请重新选择");
                }
                connection.clear();
                connection.put("savedConnectionId", stored.id());
                connection.put("savedConnectionVersion", stored.version());
            }
            body.put("connection", connection);
            request = json.convertValue(body, GenericCollectionController.Request.class);
            if (!validator.validate(request).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采集参数无效");
            var task = tasks.register(new JdbcDesktopTaskStore.Registration(grant.namespace(), taskId, grant.tenantId(),
                    jwt.getSubject(), grant.projectId(), grant.taskToken()));
            server.claim(new DesktopServerClient.TaskAuthorization(task, grant.taskToken()));
            var result = collections.submit(jwt, taskId, request);
            tasks.attach(new JdbcDesktopTaskStore.Attachment(task.key(), result.collectionId()));
            return result;
        } finally {
            if (input.password() != null) Arrays.fill(input.password(), '\0');
            if (request != null) request.clearCredentials();
        }
    }

    @GetMapping("/tasks/{taskId}")
    public CollectionQueryPort.CollectionDetails task(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @PathVariable("taskId") String taskId) {
        var grant = grant(http, "TASK_READ"); requireTask(grant, taskId);
        return reads.get(jwt, taskId, grant.namespace());
    }

    @GetMapping("/tasks/{taskId}/semantic-results")
    public List<CollectionSemanticResultController.CollectionSemanticResult> semanticResults(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http, @PathVariable("taskId") String taskId) {
        var grant = grant(http, "TASK_READ"); requireTask(grant, taskId);
        return reads.semanticResults(jwt, taskId, grant.namespace());
    }

    @GetMapping("/saved-connections")
    public ScopedConnections connections(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        var grant = grant(http, "CONNECTIONS_READ");
        var matching = connectionScopes.matching(scope(jwt, grant));
        return new ScopedConnections(connections.list(jwt, grant.namespace()).items().stream()
                .filter(connection -> matching.contains(connection.id())).toList(),
                new ConnectionScopeView(grant.projectId(), grant.connectionScope().deviceId(), grant.connectionScope().protocol()));
    }

    @PostMapping("/saved-connections")
    @org.springframework.transaction.annotation.Transactional
    public SavedConnectionService.SaveResult save(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http, @Valid @RequestBody Save input) {
        try {
            var grant = grant(http, "CONNECTION_SAVE");
            var scope = scope(jwt, grant);
            if (input.connection().protocol()==null || !scope.protocol().equals(input.connection().protocol().name())) reject();
            if (saved.find(jwt.getSubject(), grant.namespace(), input.requestKey()).isPresent()
                    && !connectionScopes.matching(scope).contains(input.requestKey())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "原连接不属于当前范围，请重新保存");
            }
            var result = connections.verifyAndCreateIdentified(jwt, new SavedConnectionController.IdentifiedCreate(input.requestKey(),
                    new SavedConnectionController.CreateRequest(grant.namespace(), input.displayName(), input.description(), input.connection())));
            if (result.saved()) connectionScopes.bind(new JdbcDesktopConnectionScopeStore.Binding(scope, result.connection().id()));
            return result;
        } finally { if (input.connection() != null) input.connection().clearCredentials(); }
    }

    private static JdbcDesktopConnectionScopeStore.Scope scope(Jwt jwt, DesktopServerClient.Grant grant) {
        if (grant.connectionScope() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "请更新服务端后重试本机连接操作");
        return new JdbcDesktopConnectionScopeStore.Scope(jwt.getSubject(),grant.namespace(),grant.projectId(),
                grant.connectionScope().deviceId(),grant.connectionScope().protocol());
    }
    public record ConnectionScopeView(String projectId, String deviceId, String protocol) { }
    public record ScopedConnections(List<com.dp.plat.deviceops.core.model.SavedConnection> items, ConnectionScopeView scope) { }

    private static DesktopServerClient.Grant grant(HttpServletRequest request, String operation) {
        Object value = request.getAttribute(DesktopTicketFilter.GRANT);
        if (!(value instanceof DesktopServerClient.Grant grant) || !operation.equals(grant.operation())) { reject(); return null; }
        return grant;
    }
    private static void requireTask(DesktopServerClient.Grant grant, String taskId) { if (!Objects.equals(taskId, grant.platformTaskId())) reject(); }
    private static void reject() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本机操作授权不匹配"); }
    public record Execute(@JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Size(max=65536) char[] password) {
        @Override public String toString() { return "DesktopExecution[redacted]"; }
    }
    public record Save(@NotBlank @Pattern(regexp="[a-zA-Z0-9-]{16,36}") String requestKey,
                       @NotBlank @Size(max=200) String displayName, @Size(max=2000) String description,
                       @NotNull @Valid ConnectionRequestMapper.Connection connection) {
        @Override public String toString() { return "DesktopConnectionSave[redacted]"; }
    }
}
