package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogArchiveApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsLogSplitApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** New archive alongside the legacy URL log. Device locks serialize version allocation across every ingress. */
@Service @RequiredArgsConstructor
public class DeviceLogArchiveService implements DeviceConfigurationLogArchiveApi {
    private final DeviceLogImportMapper imports;
    private final DeviceLogFileMapper files;
    private final DeviceLogVersionMapper versions;
    private final DeviceLogDeviceMapper deviceQueries;
    private final DeviceMapper devices;
    private final PermissionApi permissions;
    private final ObjectProvider<ProjectScopeApi> scopes;
    private final ObjectProvider<DeviceOpsLogSplitApi> dac;
    private final ObjectProvider<DeviceLogFileApi> fileStorage;
    private final ObjectProvider<FileEvidenceApi> evidence;
    private final ObjectProvider<NativeGeneratedFileApi> downloads;
    private final ObjectProvider<DeviceArchivedLogParsingService> parsing;

    @Transactional(rollbackFor=Exception.class)
    public ImportView upload(Long projectId, String operationId, String fileName, byte[] bytes) {
        UUID.fromString(operationId);
        return archive(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), projectId,
                "manual:"+operationId, "MANUAL_UPLOAD", fileName, bytes, "TERMINAL_TEXT", null);
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public void receiveCollection(CollectionLog command) {
        if (command==null || !Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.projectId()==null || command.deviceId()==null
                || command.platformTaskId()==null || command.resultVersion()==null || command.fileVersionId()==null)
            throw problem("采集日志归档来源无效");
        var origin = new DeviceLogFileApi.Origin(command.tenantId(), command.actorId(), command.projectId(), command.deviceId(),
                command.platformTaskId(), command.resultVersion(), command.fileVersionId());
        byte[] bytes = fileStorage.getObject().readCollection(origin);
        archive(command.tenantId(), command.actorId(), command.projectId(),
                "collection:"+command.platformTaskId()+":"+command.resultVersion(), "COLLECTION_CALLBACK",
                "collection-"+command.resultVersion()+".txt", bytes, "DAC_CALLBACK", origin);
    }

    private ImportView archive(Long tenant, Long actor, Long projectId, String sourceKey, String sourceType,
                               String fileName, byte[] bytes, String format, DeviceLogFileApi.Origin origin) {
        authorize(tenant, actor, projectId, true, origin != null);
        if (fileName == null || fileName.isBlank() || fileName.length()>150 || fileName.contains("/") || fileName.contains("\\")
                || fileName.chars().anyMatch(c -> c < 32) || bytes == null || bytes.length == 0 || bytes.length > 20*1024*1024)
            throw problem("日志名称或文件大小无效");
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException failure) { throw problem("请上传 UTF-8 编码的日志文本"); }
        String digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        var existing = imports.findImport(new DeviceLogImportLockQuery(tenant, sourceKey));
        if (existing != null) {
            if (!Objects.equals(existing.getProjectId(), projectId) || !sourceType.equals(existing.getSourceType())
                    || !digest.equals(existing.getInputSha256()) || !Objects.equals(existing.getActorId(), actor)
                    || !fileName.equals(existing.getSourceFileName())) throw problem("同一导入请求不能替换原文件或归属");
            return view(existing);
        }
        var splitApi = dac.getIfAvailable();
        if (splitApi == null) throw problem("DAC 日志拆分服务尚未启用");
        DeviceOpsLogSplitApi.Result split;
        var batch = new DeviceLogImportDO(); batch.setTenantId(tenant); batch.setProjectId(projectId); batch.setActorId(actor);
        batch.setSourceKey(sourceKey); batch.setSourceType(sourceType); batch.setSourceFileName(fileName); batch.setInputSha256(digest);
        batch.setCreator(actor.toString()); batch.setUpdater(actor.toString()); batch.setDeviceCount(0); batch.setRedactedValueCount(0);
        try { split = splitApi.split(new DeviceOpsLogSplitApi.Command(tenant, projectId, format, text)); }
        catch (DeviceOpsLogSplitApi.Rejected failure) { return reject(batch, failure.getMessage()); }
        var serials = new HashSet<String>();
        for (var log : split.devices()) {
            if (log.serialNumber() == null || !log.serialNumber().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                    || log.content() == null || log.content().isBlank() || !serials.add(log.serialNumber().toUpperCase(Locale.ROOT)))
                throw problem("DAC 设备日志响应无效");
        }
        if (serials.isEmpty()) throw problem("DAC 未返回设备日志");
        var matched = deviceQueries.matchSerials(new DeviceLogSerialsQuery(tenant, projectId, serials));
        var bySerial = new HashMap<String, DeviceDO>();
        for (var device : matched) {
            if (device.getSn() == null || bySerial.putIfAbsent(device.getSn().toUpperCase(Locale.ROOT), device) != null)
                return reject(batch, "项目中的设备序列号不唯一，未归档任何设备日志");
        }
        if (!bySerial.keySet().equals(serials)) return reject(batch, "日志包含未关联当前项目的设备序列号，未归档任何设备日志");
        if (origin != null && (matched.size()!=1 || !Objects.equals(matched.getFirst().getId(), origin.deviceId())))
            return reject(batch, "采集内容序列号与任务设备不一致，未归档日志");
        // Lock every device in a stable order before storing any file, so a bad association cannot partially import a batch.
        for (var located : matched.stream().sorted(Comparator.comparing(DeviceDO::getId)).toList()) {
            var locked = devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(tenant, located.getId()));
            if (locked == null || !Objects.equals(projectId, locked.getProjectId()) || !Objects.equals(locked.getSn(), located.getSn()))
                throw problem("设备归属或序列号已变化，请重新导入");
        }
        batch.setOutcome("ARCHIVED"); batch.setDeviceCount(matched.size()); batch.setRedactedValueCount(split.redactedValueCount());
        var replay = insertOrReplay(batch);
        if (replay != null) return replay;
        String nameBase = fileName.length()>120 ? fileName.substring(0,120) : fileName;
        store(batch, null, null, nameBase+".source.txt", split.sanitizedSource(), origin);
        for (var log : split.devices()) {
            var device = bySerial.get(log.serialNumber().toUpperCase(Locale.ROOT));
            var versionQuery = new DeviceLogVersionQuery(tenant, device.getId());
            if (versions.allocate(versionQuery)<1) throw problem("设备日志版本分配失败");
            Long version = versions.current(versionQuery);
            if (version == null || version<1) throw problem("设备日志版本读取失败");
            store(batch, device, version, log.serialNumber()+"-v"+version+".txt", log.content(), origin);
        }
        return view(batch);
    }

    private void store(DeviceLogImportDO batch, DeviceDO device, Long version, String fileName, String text, DeviceLogFileApi.Origin origin) {
        var row = new DeviceLogFileDO(); row.setTenantId(batch.getTenantId()); row.setProjectId(batch.getProjectId()); row.setImportId(batch.getId());
        row.setDeviceId(device==null ? null : device.getId()); row.setSerialNumber(device==null ? null : device.getSn()); row.setDeviceVersion(version);
        row.setSourceType(batch.getSourceType()); row.setFileName(fileName); row.setCreator(batch.getActorId().toString()); row.setUpdater(batch.getActorId().toString());
        if (files.insert(row)!=1) throw problem("设备日志记录保存失败");
        var file = fileStorage.getObject().store(new DeviceLogFileApi.Command(batch.getTenantId(), batch.getActorId(),
                "device-log-file:"+row.getId(), row.getId(), fileName, text.getBytes(StandardCharsets.UTF_8), origin));
        row.setMaterialId(file.materialId()); row.setFileReferenceId(file.referenceId()); row.setArtifactId(file.artifactId());
        row.setArtifactVersion(file.versionNo()); row.setSha256(file.sha256());
        if (files.updateById(row)!=1) throw problem("设备日志文件登记失败");
        if (device!=null) {
            var parser=parsing.getIfAvailable();
            if (parser!=null) parser.register(row,text);
        }
    }
    private ImportView reject(DeviceLogImportDO batch, String reason) {
        batch.setOutcome("REJECTED"); batch.setRejectionReason(reason);
        var replay = insertOrReplay(batch);
        if (replay != null) return replay;
        return view(batch);
    }
    private ImportView insertOrReplay(DeviceLogImportDO batch) {
        try {
            if (imports.insert(batch)!=1) throw problem("日志导入记录保存失败");
            return null;
        } catch (DuplicateKeyException collision) {
            // A concurrent request may have committed after the initial snapshot. Read its immutable result now.
            var existing = imports.lockImport(new DeviceLogImportLockQuery(batch.getTenantId(), batch.getSourceKey()));
            if (existing == null) throw collision;
            if (!Objects.equals(existing.getProjectId(), batch.getProjectId())
                    || !Objects.equals(existing.getSourceType(), batch.getSourceType())
                    || !Objects.equals(existing.getInputSha256(), batch.getInputSha256())
                    || !Objects.equals(existing.getActorId(), batch.getActorId())
                    || !Objects.equals(existing.getSourceFileName(), batch.getSourceFileName()))
                throw problem("同一导入请求不能替换原文件或归属");
            return view(existing, files.lockImportFiles(new DeviceLogImportFilesQuery(existing.getTenantId(),existing.getId())));
        }
    }
    private ImportView view(DeviceLogImportDO batch) {
        return view(batch,files.importFiles(new DeviceLogImportFilesQuery(batch.getTenantId(), batch.getId())));
    }
    private ImportView view(DeviceLogImportDO batch, List<DeviceLogFileDO> importedFiles) {
        return new ImportView(batch.getId(), batch.getOutcome(), batch.getRejectionReason(), batch.getDeviceCount(), batch.getRedactedValueCount(),
                importedFiles.stream().map(this::fileView).toList());
    }

    @Transactional(rollbackFor=Exception.class)
    public List<DeviceView> latest(Long projectId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        authorize(tenant, SecurityFrameworkUtils.getLoginUserId(), projectId, false);
        var latest = new HashMap<Long, FileView>();
        for (var row : files.projectFiles(new DeviceLogProjectQuery(tenant, projectId))) {
            var current = latest.get(row.getDeviceId());
            if (current == null || !current.available() && available(row)) {
                latest.put(row.getDeviceId(), fileView(row));
            }
        }
        return deviceQueries.projectDevices(new DeviceLogProjectQuery(tenant, projectId)).stream()
                .filter(d -> latest.containsKey(d.getId()))
                .map(d -> new DeviceView(d.getId(), d.getSn(), d.getName(), latest.get(d.getId()))).toList();
    }
    @Transactional(rollbackFor=Exception.class)
    public List<FileView> history(Long projectId, Long deviceId) {
        Long tenant = TenantContextHolder.getRequiredTenantId(); authorize(tenant, SecurityFrameworkUtils.getLoginUserId(), projectId, false);
        var device = devices.selectByTenantAndId(tenant, deviceId);
        if (device==null || !Objects.equals(projectId, device.getProjectId())) throw problem("设备不属于当前项目");
        return files.history(new DeviceLogHistoryQuery(tenant, projectId, deviceId)).stream().map(this::fileView).toList();
    }
    @Transactional(rollbackFor=Exception.class)
    public ImportView getImport(Long id) {
        var row = imports.selectById(id);
        if (row==null || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw problem("日志导入记录不存在");
        authorize(row.getTenantId(), SecurityFrameworkUtils.getLoginUserId(), row.getProjectId(), false);
        return view(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public String download(Long fileId) {
        var row = files.selectById(fileId);
        if (row==null || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId()) || row.getMaterialId()==null)
            throw problem("日志文件不存在");
        authorize(row.getTenantId(), SecurityFrameworkUtils.getLoginUserId(), row.getProjectId(), false);
        if (!permissions.hasAnyPermissions(SecurityFrameworkUtils.getLoginUserId(), "pms:device-log-archive:download")) throw problem("日志下载权限不足");
        return downloads.getObject().requestDownload("AST", "device-log-file", row.getId(), row.getMaterialId());
    }
    private boolean available(DeviceLogFileDO row) {
        var document = evidence.getObject().inspectDocument(row.getTenantId(), row.getFileReferenceId());
        return document!=null && document.available() && Objects.equals(row.getSha256(), document.sha256());
    }
    private FileView fileView(DeviceLogFileDO row) {
        return new FileView(row.getId(), row.getDeviceId(), row.getSerialNumber(), row.getDeviceVersion(), row.getSourceType(),
                row.getFileName(), row.getImportId(), row.getMaterialId(), row.getCreateTime(), available(row));
    }
    private void authorize(Long tenant, Long actor, Long projectId, boolean write) {
        authorize(tenant, actor, projectId, write, false);
    }
    private void authorize(Long tenant, Long actor, Long projectId, boolean write, boolean collection) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || actor==null || projectId==null || projectId<=0
                || !permissions.hasAnyPermissions(actor, write ? (collection ? "pms:imp-configuration:update" : "pms:device-log-archive:import")
                        : "pms:device-log-archive:query"))
            throw problem("设备日志访问权限不足");
        if (write && !collection && !permissions.hasAnyPermissions(actor,"pms:file:upload"))
            throw problem("设备日志上传权限不足");
        String action = write ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_VIEW;
        var current = scopes.getObject().resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(projectId)
                .actionCode(action).build());
        if (current==null || current.treeVersion()==null || current.fullProjectIds()==null
                || !current.fullProjectIds().contains(projectId)) throw problem("项目不在当前数据范围");
        var scope = write ? scopes.getObject().lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(projectId)
                .actionCode(action)
                .expectedScopeVersion(current.treeVersion()).build()) : current;
        if (write && (scope==null || !Objects.equals(scope.treeVersion(),current.treeVersion())))
            throw problem("项目范围已变化，请刷新后重试");
        if (scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(projectId)) throw problem("项目不在当前数据范围");
    }
    private BusinessContractException problem(String message) { return new BusinessContractException("DEVICE_LOG_ARCHIVE_REJECTED", message); }
    public record FileView(Long id, Long deviceId, String serialNumber, Long deviceVersion, String sourceType, String fileName,
                           Long importId, Long materialId, java.time.LocalDateTime archivedAt, boolean available) { }
    public record DeviceView(Long deviceId, String serialNumber, String deviceName, FileView latest) { }
    public record ImportView(Long id, String outcome, String rejectionReason, Integer deviceCount, Integer redactedValueCount, List<FileView> files) { }
}
