package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogFileDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceLogFileMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogFileLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component @RequiredArgsConstructor
public class DeviceLogFilePolicyProvider implements FileBusinessObjectPolicyProvider {
    public static final String PURPOSE = "DEVICE_CONFIGURATION_LOG";
    public static final String OBJECT = "DeviceConfigurationLogFile";
    private final DeviceLogFileMapper files;
    private final ObjectProvider<ProjectScopeApi> scopes;
    private final PermissionApi permissions;
    @Override public String ownerContext() { return "AST"; }
    @Override public String objectType() { return OBJECT; }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return read(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(), query.requiredAction(), false, null);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return read(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(), query.requiredAction(), true, query.expectedScopeVersion());
    }
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        return read(query.tenantId(), query.actorUserId(), query.key().objectId(), query.key().purposeCode(), query.requiredAction(), false, null);
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query) {
        return read(query.tenantId(), query.actorUserId(), query.key().objectId(), query.key().purposeCode(), query.requiredAction(), true, query.expectedScopeVersion());
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateNativeGeneratedFile(NativeGeneratedFilePolicyQuery query) {
        if (!Objects.equals(query.expectedOwnerVersion(), 1L)) return denied();
        Long expectedScope=query.expectedScopeVersion();
        if (expectedScope==null) {
            var initial=read(query.tenantId(),query.actorUserId(),String.valueOf(query.objectId()),query.purposeCode(),"UPLOAD",false,null);
            if (!initial.allowed() || initial.scopeVersion()==null) return denied();
            expectedScope=initial.scopeVersion();
        }
        return read(query.tenantId(), query.actorUserId(), String.valueOf(query.objectId()), query.purposeCode(), "UPLOAD", true, expectedScope);
    }
    private FileBusinessObjectPolicyFact read(Long tenant, Long actor, String id, String purpose, String action, boolean lock, Long expectedScope) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || actor == null || !PURPOSE.equals(purpose)) return denied();
        Long key;
        try { key = Long.valueOf(id); } catch (RuntimeException failure) { return denied(); }
        DeviceLogFileDO row = files.selectById(key);
        if (row == null || !tenant.equals(row.getTenantId())) return denied();
        boolean write = "UPLOAD".equals(action);
        if (!write && !Set.of("READ", "DOWNLOAD", "PREVIEW").contains(action)) return denied();
        String permission = write ? ("COLLECTION_CALLBACK".equals(row.getSourceType()) ? "pms:imp-configuration:update" : "pms:device-log-archive:import") : ("READ".equals(action)
                ? "pms:device-log-archive:query" : "pms:device-log-archive:download");
        if (!permissions.hasAnyPermissions(actor, permission)) return denied();
        String scopeAction = write ? ProjectScopeApi.ACTION_EDIT : ProjectScopeApi.ACTION_VIEW;
        var scope = lock ? scopes.getObject().lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(row.getProjectId())
                .actionCode(scopeAction)
                .expectedScopeVersion(expectedScope).build())
                : scopes.getObject().resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(row.getProjectId())
                .actionCode(scopeAction).build());
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(row.getProjectId())) return denied();
        if (lock && !Objects.equals(expectedScope,scope.treeVersion())) return denied();
        if (lock) row = files.lockFile(new DeviceLogFileLockQuery(tenant, key));
        if (row == null || (write && row.getFileReferenceId() != null)) return denied();
        return new FileBusinessObjectPolicyFact(true, scope.treeVersion(), "IMMUTABLE", "SINGLE", Set.of(PURPOSE),
                Set.of("text/plain"), 20L * 1024 * 1024, "INTERNAL");
    }
    private FileBusinessObjectPolicyFact denied() { return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null); }
}
