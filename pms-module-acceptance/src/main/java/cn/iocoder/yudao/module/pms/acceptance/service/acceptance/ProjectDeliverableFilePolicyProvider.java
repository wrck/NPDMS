package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component @RequiredArgsConstructor
public class ProjectDeliverableFilePolicyProvider implements FileBusinessObjectPolicyProvider {
    public static final String OWNER = "ACC", TYPE = "PROJECT_DELIVERABLE", PURPOSE = "PROJECT_DELIVERABLE_DOCUMENT";
    private final AccProjectDeliverableMapper deliverables;
    private final ProjectDeliverableRuleApi rules;
    private final ProjectDeliverableAccess access;
    @Override public String ownerContext() { return OWNER; }
    @Override public String objectType() { return TYPE; }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery q) {
        return policy(q.tenantId(), q.actorUserId(), q.objectId(), q.purposeCode(), q.requiredAction(), false, null);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery q) {
        return policy(q.tenantId(), q.actorUserId(), q.objectId(), q.purposeCode(), q.requiredAction(), true, q.expectedScopeVersion());
    }
    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery q) {
        if (!owns(q.key())) return denied();
        return policy(q.tenantId(), q.actorUserId(), q.key().objectId(), q.key().purposeCode(), q.requiredAction(), false, null);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery q) {
        if (!owns(q.key())) return denied();
        return policy(q.tenantId(), q.actorUserId(), q.key().objectId(), q.key().purposeCode(), q.requiredAction(), true, q.expectedScopeVersion());
    }
    private static boolean owns(FileReferenceSetKey key) {
        return key != null && OWNER.equals(key.ownerContext()) && TYPE.equals(key.objectType());
    }
    private FileBusinessObjectPolicyFact policy(Long tenant, Long actor, String id, String purpose, String action, boolean lock, Long expectedScope) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || !PURPOSE.equals(purpose)
                || !Set.of("UPLOAD", "READ", "DOWNLOAD", "PREVIEW").contains(action)) return denied();
        Long objectId;
        try { objectId = Long.valueOf(id); } catch (NumberFormatException invalid) { return denied(); }
        var row = deliverables.selectById(objectId);
        if (row == null || !Objects.equals(row.getTenantId(), tenant)) return denied();
        var context = lock ? rules.lock(row.getProjectId(), row.getDeliverableCode()) : rules.read(row.getProjectId(), row.getDeliverableCode());
        if (lock) {
            var current = deliverables.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(tenant, objectId));
            if (current == null || !Objects.equals(row.getProjectId(), current.getProjectId())
                    || !Objects.equals(row.getDeliverableCode(), current.getDeliverableCode())) return denied();
        }
        boolean write = "UPLOAD".equals(action);
        if (write && !ProjectDeliverableSubmissionService.allowed(context.configuration(), "UPLOAD")) return denied();
        Long scope = access.check(context, tenant, actor, write, lock, expectedScope);
        // Reuse the controlled document uploader's supported media and platform 50 MiB limit.
        return new FileBusinessObjectPolicyFact(true, scope, "IMMUTABLE", "MULTIPLE", Set.of(PURPOSE),
                Set.of("application/pdf", "image/jpeg", "image/png"), 52_428_800L, "INTERNAL");
    }
    private static FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, "IMMUTABLE", "MULTIPLE", Set.of(), Set.of(), 0L, "INTERNAL");
    }
}
