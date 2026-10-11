package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSemanticFacts;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.Objects;

/** Files commit before parser submission; polling never reissues a device command or a parse submission. */
@Service @RequiredArgsConstructor
public class DeviceArchivedLogParsingService {
    private final DeviceLogParseJobMapper jobs;
    private final DeviceLogFileMapper files;
    private final DeviceLogImportMapper imports;
    private final ObjectProvider<DeviceLogParsingApi> parsers;
    private final DeviceLogParsedFactApi projection;
    private final ProjectScopeApi scopes;
    private final PlatformTransactionManager transactions;
    private final cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi evidence;
    private final DeviceLogSlotProjectionService slots;

    public void register(DeviceLogFileDO file,String sanitizedContent) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("设备日志解析登记必须与文件归档一起提交");
        var batch=imports.selectById(file.getImportId());
        if (batch==null || !Objects.equals(batch.getTenantId(),file.getTenantId())) throw new IllegalArgumentException("日志批次不存在");
        var job=new DeviceLogParseJobDO();job.setId(file.getId());job.setTenantId(file.getTenantId());
        job.setProjectId(file.getProjectId());job.setActorId(batch.getActorId());
        var received=LocalDateTime.now();job.setLogReceivedAt(received.withNano((received.getNano()/1000000)*1000000));
        job.setProjected(false);
        job.setState("SUBMITTING");job.setCreator(batch.getActorId().toString());job.setUpdater(batch.getActorId().toString());
        if (jobs.insert(job)!=1) throw new IllegalStateException("日志解析登记失败");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    var parser=parsers.getIfAvailable();
                    if (parser==null) return;
                    var remote=parser.submit(new DeviceLogParsingApi.Submission(requestId(job),file.getProjectId().toString(),null,sanitizedContent,null));
                    saveObserved(job,remote);
                } catch (RuntimeException uncertain) {
                    // The file is already committed. SUBMITTING means query the immutable request on refresh.
                }
            }
        });
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public View get(Long fileId) {
        readableFile(fileId);
        return reconcile(fileId);
    }

    private DeviceLogFileDO readableFile(Long fileId) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        var file=files.selectById(fileId);
        if (file==null || !Objects.equals(file.getTenantId(),tenant) || file.getDeviceId()==null)
            throw new IllegalArgumentException("设备日志不存在");
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,SecurityFrameworkUtils.getLoginUserId(),file.getProjectId(),ProjectScopeApi.ACTION_VIEW));
        if (scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(file.getProjectId()))
            throw new IllegalArgumentException("设备日志不在当前项目范围");
        return file;
    }

    /** Read immutable structured results without projecting history or reissuing a parse. */
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public ResultView getResult(Long fileId) {
        var file=readableFile(fileId);
        var job=jobs.selectById(fileId);
        if (job==null) return new ResultView("NOT_REGISTERED",null,null,"该历史日志尚无解析任务",null);
        if (!Objects.equals(job.getTenantId(),file.getTenantId()) || !Objects.equals(job.getProjectId(),file.getProjectId()))
            throw new IllegalArgumentException("日志解析来源已变化");
        var parser=parsers.getIfAvailable();
        if (parser==null) return new ResultView(job.getState(),job.getExternalTaskId(),job.getParserVersion(),"日志解析服务尚未启用",null);
        DeviceLogParsingApi.ParseTask remote;
        try { remote=parser.find(requestId(job),job.getProjectId().toString(),null); }
        catch (DeviceLogParsingApi.ParsingException unavailable) {
            return new ResultView(job.getState(),job.getExternalTaskId(),job.getParserVersion(),"解析查询暂不可用，请刷新重试",null);
        }
        if (remote==null) return new ResultView("SUBMITTING",null,null,"解析受理尚未确认，请刷新重试",null);
        if (job.getExternalTaskId()!=null && !Objects.equals(job.getExternalTaskId(),remote.taskId()))
            throw new IllegalArgumentException("日志解析任务标识不能变化");
        if (!"SUCCEEDED".equals(remote.state()))
            return new ResultView(remote.state(),remote.taskId(),remote.releaseVersion(),"日志解析尚未成功完成",null);
        if (!availableEvidence(file))
            return new ResultView(remote.state(),remote.taskId(),remote.releaseVersion(),"归档日志不可用，无法查看解析结果",null);
        var values=DeviceLogSemanticFacts.from(remote.result());
        if (values.serialNumber()!=null && !file.getSerialNumber().equalsIgnoreCase(values.serialNumber()))
            return new ResultView(remote.state(),remote.taskId(),remote.releaseVersion(),"解析序列号与归档设备不一致",null);
        return new ResultView(remote.state(),remote.taskId(),remote.releaseVersion(),null,remote.result());
    }

    private boolean availableEvidence(DeviceLogFileDO file) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        var document=evidence.inspectDocument(tenant,file.getFileReferenceId());
        if (document==null || !document.available() || !Objects.equals(document.sha256(),file.getSha256())
                || !"AST".equals(document.ownerContext()) || !"DeviceConfigurationLogFile".equals(document.objectType())
                || !file.getId().toString().equals(document.objectId()) || !Objects.equals(file.getArtifactId(),document.artifactId())
                || !Objects.equals(file.getArtifactVersion(),document.versionNo())) return false;
        var valid=evidence.lockAndRevalidate(new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Query(
                tenant,document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),
                document.objectId(),document.purposeCode(),document.referenceKey(),document.sha256()));
        return valid!=null && valid.valid();
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public View reconcile(Long fileId) {
        Long tenant=TenantContextHolder.getRequiredTenantId();
        var file=files.selectById(fileId);
        if (file==null || !Objects.equals(file.getTenantId(),tenant) || file.getDeviceId()==null)
            throw new IllegalArgumentException("设备日志不存在");
        var job=jobs.selectById(fileId);
        if (job==null) return new View("NOT_REGISTERED",null,null,null,null,"该历史日志尚无解析任务");
        if (!Objects.equals(job.getTenantId(),tenant) || !Objects.equals(job.getProjectId(),file.getProjectId()))
            throw new IllegalArgumentException("日志解析来源已变化");
        var parser=parsers.getIfAvailable();
        if (parser==null) return new View(job.getState(),job.getExternalTaskId(),job.getParserVersion(),null,null,"日志解析服务尚未启用");
        DeviceLogParsingApi.ParseTask remote;
        try {
            remote=parser.find(requestId(job),job.getProjectId().toString(),null);
            if (remote==null) return new View("SUBMITTING",null,null,null,null,"解析受理尚未确认；刷新查询，未受理时请重新上传");
        } catch (DeviceLogParsingApi.ParsingException unavailable) {
            return new View(job.getState(),job.getExternalTaskId(),job.getParserVersion(),null,null,"解析查询暂不可用，请刷新重试");
        }
        saveObserved(job,remote);
        DeviceLogParsedFactApi.Outcome association=null;
        String version=null;
        if ("SUCCEEDED".equals(remote.state())) {
            if (!availableEvidence(file))
                return new View(remote.state(),remote.taskId(),remote.releaseVersion(),null,null,"归档日志不可用，未更新设备");
            var values=DeviceLogSemanticFacts.from(remote.result());
            var slotFacts=cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSlotFacts.from(remote.result());
            if (values.serialNumber()!=null && !file.getSerialNumber().equalsIgnoreCase(values.serialNumber()))
                return new View(remote.state(),remote.taskId(),remote.releaseVersion(),null,null,"解析序列号与归档设备不一致，未更新设备");
            if (!slotFacts.devices().isEmpty()) slots.prepare(tenant);
            var parentFact=new DeviceLogParsedFactApi.ParsedLog(tenant,job.getActorId(),job.getProjectId(),
                    requestId(job),"ast-file:"+fileId,job.getLogReceivedAt(),remote.taskId(),remote.releaseVersion(),fileId,
                    file.getSerialNumber(),values.conpVersion(),values.bootVersion(),values.cpldVersion(),values.pcbVersion(),file.getDeviceVersion());
            association=projection.record(parentFact);
            slots.record(parentFact,slotFacts,association);
            version=values.conpVersion();
            var done=new DeviceLogParseJobDO();done.setId(fileId);done.setProjected(true);jobs.updateById(done);
        }
        return new View(remote.state(),remote.taskId(),remote.releaseVersion(),association,version,association==null ? null : association.message());
    }

    private void saveObserved(DeviceLogParseJobDO original,DeviceLogParsingApi.ParseTask remote) {
        new TransactionTemplate(transactions,requiresNew()).executeWithoutResult(tx -> {
            var current=jobs.lock(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogParseJobLockQuery(original.getTenantId(),original.getId()));
            if (current==null || !Objects.equals(current.getTenantId(),original.getTenantId())) throw new IllegalArgumentException("日志解析任务不存在");
            if (current.getExternalTaskId()!=null && !current.getExternalTaskId().equals(remote.taskId()))
                throw new IllegalArgumentException("日志解析任务标识不能变化");
            if (!java.util.Set.of("SUCCEEDED","FAILED","CANCELLED").contains(current.getState())) {
                current.setState(remote.state());current.setExternalTaskId(remote.taskId());current.setParserVersion(remote.releaseVersion());
                if (jobs.updateById(current)!=1) throw new IllegalStateException("日志解析状态登记失败");
            }
        });
    }
    private DefaultTransactionDefinition requiresNew() {
        var definition=new DefaultTransactionDefinition();
        definition.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);return definition;
    }
    private String requestId(DeviceLogParseJobDO job) {
        return java.util.UUID.nameUUIDFromBytes(("ast-log:"+job.getTenantId()+":"+job.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    public record View(String state,String taskId,String parserVersion,DeviceLogParsedFactApi.Outcome deviceProjection,
                       String conpVersion,String message) { }
    public record ResultView(String state,String taskId,String parserVersion,String message,
                             java.util.Map<String,Object> result) { }
}
