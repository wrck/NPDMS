package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSemanticFacts;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.DeviceLogParseDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionRequestMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.DeviceLogParseMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.DeviceLogParsePageQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Local authorization and source identity only. DAC remains the parser/input/result owner. */
@Service
@RequiredArgsConstructor
public class DeviceLogParsingService {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "CANCELLED");
    private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
    private final DeviceLogParseMapper parses;
    private final CollectionRequestMapper requests;
    private final CollectionTaskMapper tasks;
    private final FileVersionMapper files;
    private final CollectionAuthorization auth;
    private final List<CollectionSourceAdapter> sources;
    private final ObjectProvider<DeviceLogParsingApi> providers;
    private final ObjectProvider<DeviceLogParsedFactApi> assetFacts;
    private final PlatformTransactionManager transactions;

    public View submit(Long actor, Input input) {
        validate(input);
        DeviceLogParsingApi provider = provider();
        Source source = authorizeInput(actor, input, true, false);
        String digest = CollectionTemplateService.hash(JsonUtils.toJsonString(Arrays.asList(actor, source.projectId(),
                input.inputName(), input.commandHint(), input.inputText(), input.entry(), input.objectId(),
                input.executionId(), source.collectionId(), source.fileVersionId())));
        Prepared prepared;
        try {
            prepared = new TransactionTemplate(transactions).execute(tx -> {
                Source locked = authorizeInput(actor, input, true, true);
                if (!source.equals(locked)) throw new CollectionOperationException("日志归属已变化，请刷新后重试");
                var existing = parses.selectByRequestKeyForUpdate(tenant(), input.requestKey());
                if (existing != null) return replay(existing, digest);
                var row = new DeviceLogParseDO();
                row.setTenantId(tenant()); row.setProjectId(source.projectId()); row.setActorId(actor);
                row.setRequestKey(input.requestKey()); row.setRequestDigest(digest); row.setCreator(actor.toString());
                row.setInputName(input.executionId() == null ? input.inputName() : "采集日志 " + input.executionId());
                row.setSourceEntry(input.entry()); row.setSourceObjectId(input.objectId());
                row.setSourceExecutionId(input.executionId()); row.setSourceCollectionId(source.collectionId());
                row.setSourceFileVersionId(source.fileVersionId()); row.setState("SUBMITTING");
                parses.insert(row);
                return new Prepared(row, true);
            });
        } catch (DuplicateKeyException concurrent) {
            prepared = replay(parses.selectByRequestKey(tenant(), input.requestKey()), digest);
        }
        DeviceLogParseDO row = prepared.row();
        try {
            if (!prepared.fresh()) {
                var accepted = provider.find(remoteId(row), row.getProjectId().toString(), row.getSourceCollectionId());
                if (accepted != null) return observed(row, accepted);
            }
            return observed(row, provider.submit(new DeviceLogParsingApi.Submission(remoteId(row),
                    row.getProjectId().toString(), row.getSourceCollectionId(), input.inputText(), input.commandHint())));
        } catch (DeviceLogParsingApi.ParsingException failure) {
            return view(row, null, failure.getMessage());
        }
    }

    public PageResult<View> page(Long actor, Long project, String entry, Long object, Long execution,
                                 int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 50) throw new CollectionOperationException("分页参数无效");
        if (execution == null) {
            if (entry != null || object != null) throw new CollectionOperationException("日志来源参数不完整");
            auth.permission(actor, "pms:device-collection:query"); auth.project(actor, project, false, false);
        } else {
            Source source = authorizeCollection(actor, entry, object, execution, false, false);
            if (!source.projectId().equals(project)) throw new CollectionOperationException("日志项目不匹配");
        }
        var page = parses.selectPage(new DeviceLogParsePageQuery(tenant(), project, entry, object, execution, pageNo, pageSize));
        return new PageResult<>(page.getList().stream().map(row -> view(row, null, null)).toList(), page.getTotal());
    }

    public View get(Long actor, Long id) {
        var row = parses.selectById(id);
        if (row == null || !tenant().equals(row.getTenantId())) throw new CollectionOperationException("解析记录不存在");
        authorizeRead(actor, row);
        try {
            var current = provider().find(remoteId(row), row.getProjectId().toString(), row.getSourceCollectionId());
            if (current == null) {
                return view(row, null, "DAC 尚未受理此请求；请使用原日志与原请求重试，或新建解析任务");
            }
            return observed(row, current);
        } catch (DeviceLogParsingApi.ParsingException failure) {
            return view(row, null, failure.getMessage());
        }
    }

    private void authorizeRead(Long actor, DeviceLogParseDO row) {
        if (row.getSourceExecutionId() == null) {
            auth.permission(actor, "pms:device-collection:query"); auth.project(actor, row.getProjectId(), false, false);
        } else {
            Source source = authorizeCollection(actor, row.getSourceEntry(), row.getSourceObjectId(), row.getSourceExecutionId(), false, false);
            if (!Objects.equals(source.projectId(), row.getProjectId())
                    || !Objects.equals(source.collectionId(), row.getSourceCollectionId())
                    || !Objects.equals(source.fileVersionId(), row.getSourceFileVersionId())) {
                throw new CollectionOperationException("原日志归属或版本已变化，拒绝读取此解析记录");
            }
        }
    }

    private Source authorizeInput(Long actor, Input input, boolean edit, boolean lock) {
        if (input.executionId() != null) {
            Source source = authorizeCollection(actor, input.entry(), input.objectId(), input.executionId(), edit, lock);
            if (input.projectId() != null && !input.projectId().equals(source.projectId())) {
                throw new CollectionOperationException("日志项目不匹配");
            }
            return source;
        }
        auth.permission(actor, edit ? "pms:device-collection:execute" : "pms:device-collection:query");
        auth.project(actor, input.projectId(), edit, lock);
        return new Source(input.projectId(), null, null);
    }

    private Source authorizeCollection(Long actor, String entry, Long object, Long execution, boolean edit, boolean lock) {
        if (entry == null || object == null || execution == null) throw new CollectionOperationException("请选择完整的采集日志来源");
        var adapter = sources.stream().filter(source -> entry.equals(source.entry())).findFirst()
                .orElseThrow(() -> new CollectionOperationException("此业务尚未接入日志解析"));
        var access = !edit ? CollectionSourceAdapter.Access.READ
                : lock ? CollectionSourceAdapter.Access.CONSUME : CollectionSourceAdapter.Access.CANCEL;
        var source = adapter.authorize(tenant(), actor, object, null, access, null);
        auth.permission(actor, "pms:file:download");
        var request = requests.selectById(execution);
        if (request == null || !tenant().equals(request.getTenantId()) || !entry.equals(request.getEntry())
                || !object.equals(request.getObjectId())) throw new CollectionOperationException("采集日志不存在");
        CollectionTaskDO task = tasks.selectByTenantAndPlatformTaskId(tenant(), request.getPlatformTaskId());
        if (task == null || !source.projectId().toString().equals(task.getProjectId())
                || !source.sourceContext().equals(task.getSourceContext())
                || !source.sourceObjectType().equals(task.getSourceObjectType())
                || !object.toString().equals(task.getSourceObjectId())
                || (source.deviceId() != null && !source.deviceId().toString().equals(task.getDeviceId()))
                || task.getFileVersionId() == null || task.getExternalTaskId() == null) {
            throw new CollectionOperationException("原日志归属已变化或日志尚不可用");
        }
        var version = files.selectByTenantAndId(tenant(), task.getFileVersionId());
        if (version == null || !tenant().equals(version.getTenantId())
                || !"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            throw new CollectionOperationException("日志已隔离或不可用，不能解析");
        }
        return new Source(source.projectId(), task.getExternalTaskId(), task.getFileVersionId());
    }

    private View observed(DeviceLogParseDO row, DeviceLogParsingApi.ParseTask remote) {
        DeviceLogParseDO current = new TransactionTemplate(transactions).execute(tx -> {
            var locked = parses.selectByTenantAndIdForUpdate(tenant(), row.getId());
            if (locked == null) throw new CollectionOperationException("解析记录不存在");
            if (locked.getExternalTaskId() != null && !locked.getExternalTaskId().equals(remote.taskId())) {
                throw new DeviceLogParsingApi.ParsingException("DAC 解析任务标识发生变化，拒绝读取结果");
            }
            if (!TERMINAL.contains(locked.getState())) {
                var change = new DeviceLogParseDO(); change.setId(locked.getId());
                change.setState(remote.state()); change.setExternalTaskId(remote.taskId());
                change.setReleaseVersion(remote.releaseVersion()); change.setWaitReason(remote.waitReason());
                parses.updateById(change);
                locked.setState(remote.state()); locked.setExternalTaskId(remote.taskId());
                locked.setReleaseVersion(remote.releaseVersion()); locked.setWaitReason(remote.waitReason());
            }
            return locked;
        });
        DeviceLogParsedFactApi.Outcome projection=null;
        if ("SUCCEEDED".equals(remote.state())) {
            var api=assetFacts.getIfAvailable();
            if (api==null) projection=new DeviceLogParsedFactApi.Outcome("NOT_CONNECTED",null,null,"NOT_UPDATED","设备解析关联服务尚未启用");
            else {
                var values=DeviceLogSemanticFacts.from(remote.result());
                LocalDateTime received=current.getCreateTime();
                if (current.getSourceFileVersionId()!=null) {
                    var version=files.selectByTenantAndId(tenant(),current.getSourceFileVersionId());
                    received=version==null ? null : version.getCreatedAt();
                }
                if (received==null) projection=new DeviceLogParsedFactApi.Outcome("SOURCE_UNAVAILABLE",null,values.serialNumber(),"NOT_UPDATED","原日志接收时间不可用，未更新设备");
                else {
                    try {
                        projection=api.record(new DeviceLogParsedFactApi.ParsedLog(tenant(),current.getActorId(),current.getProjectId(),
                                "plt-parse:"+current.getId(),current.getSourceFileVersionId()==null ? "plt-upload:"+current.getId() : "plt-file:"+current.getSourceFileVersionId(),
                                received,remote.taskId(),remote.releaseVersion(),current.getSourceFileVersionId(),
                                values.serialNumber(),values.conpVersion(),values.bootVersion(),values.cpldVersion(),values.pcbVersion()));
                    } catch (RuntimeException rejected) {
                        projection=new DeviceLogParsedFactApi.Outcome("PROJECTION_FAILED",null,values.serialNumber(),"NOT_UPDATED","设备解析关联未完成，请刷新重试");
                    }
                }
            }
        }
        var result=view(current,remote,null);
        return new View(result.id(),result.requestKey(),result.inputName(),result.projectId(),result.entry(),result.objectId(),
                result.executionId(),result.sourceFileVersionId(),result.state(),result.taskId(),result.releaseVersion(),result.waitReason(),
                result.createTime(),result.warnings(),result.result(),result.message(),projection);
    }

    private Prepared replay(DeviceLogParseDO row, String digest) {
        if (row == null || !digest.equals(row.getRequestDigest())) {
            throw new CollectionOperationException("原解析请求内容已变化，请新建解析任务");
        }
        return new Prepared(row, false);
    }

    private void validate(Input input) {
        if (input == null || input.requestKey() == null || !input.requestKey().matches("[a-zA-Z0-9-]{16,64}")) {
            throw new CollectionOperationException("解析请求标识无效");
        }
        if (input.executionId() != null) {
            if (input.inputText() != null || input.commandHint() != null || input.inputName() != null) {
                throw new CollectionOperationException("采集日志不能混入上传内容或替换命令");
            }
        } else {
            if (input.entry() != null || input.objectId() != null || input.inputName() == null
                    || input.inputName().isBlank() || input.inputName().length() > 200
                    || input.inputName().matches("(?s).*[\\r\\n\\x00/\\\\].*")) {
                throw new CollectionOperationException("日志文件名或来源无效");
            }
            if (input.inputText() == null || input.inputText().isBlank() || input.inputText().indexOf('\0') >= 0
                    || input.inputText().length() > MAX_INPUT_BYTES
                    || input.inputText().getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
                throw new CollectionOperationException("请提供非空文本日志，UTF-8 内容不得超过 8 MiB");
            }
            if (input.commandHint() != null && (input.commandHint().length() > 256
                    || input.commandHint().contains("\r") || input.commandHint().contains("\n"))) {
                throw new CollectionOperationException("日志命令提示仅支持一行，最多 256 字符");
            }
        }
    }

    private View view(DeviceLogParseDO row, DeviceLogParsingApi.ParseTask remote, String message) {
        return new View(row.getId(), row.getRequestKey(), row.getInputName(), row.getProjectId(), row.getSourceEntry(),
                row.getSourceObjectId(), row.getSourceExecutionId(), row.getSourceFileVersionId(), row.getState(),
                row.getExternalTaskId(), row.getReleaseVersion(), remote == null ? row.getWaitReason() : remote.waitReason(),
                row.getCreateTime(), remote == null ? List.of() : remote.warnings(),
                remote == null ? null : remote.result(), message, null);
    }

    private DeviceLogParsingApi provider() {
        var value = providers.getIfAvailable();
        if (value == null) throw new CollectionOperationException("DAC 日志解析服务尚未启用");
        return value;
    }
    private Long tenant() { return CollectionAuthorization.tenant(); }
    private String remoteId(DeviceLogParseDO row) { return "log-" + row.getId(); }
    private record Prepared(DeviceLogParseDO row, boolean fresh) { }
    private record Source(Long projectId, String collectionId, Long fileVersionId) { }
    public record Input(String requestKey, Long projectId, String inputName, String inputText, String commandHint,
                        String entry, Long objectId, Long executionId) { }
    public record View(Long id, String requestKey, String inputName, Long projectId, String entry, Long objectId,
                       Long executionId, Long sourceFileVersionId, String state, String taskId, String releaseVersion,
                       String waitReason, LocalDateTime createTime, List<String> warnings,
                       Map<String, Object> result, String message, DeviceLogParsedFactApi.Outcome deviceProjection) { }
}
