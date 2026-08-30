package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskActorQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlatformExportFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    static final String OWNER = "PLATFORM";
    static final String TYPE = "EXPORT_TASK";
    static final String PURPOSE = "EXPORT_FILE";
    private static final Set<String> ACTIONS = Set.of(FileActionCodes.UPLOAD, FileActionCodes.READ,
            FileActionCodes.DOWNLOAD, FileActionCodes.INVALIDATE);
    private final PlatformExportTaskMapper taskMapper;

    @Override public String ownerContext() { return OWNER; }
    @Override public String objectType() { return TYPE; }

    @Override
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return resolve(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.referenceKey(), query.requiredAction(), null);
    }

    @Override
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return resolve(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.referenceKey(), query.requiredAction(), query.expectedScopeVersion());
    }

    private FileBusinessObjectPolicyFact resolve(Long tenantId, Long actorId, String objectId, String purpose,
                                                  String referenceKey, String action, Long expectedScopeVersion) {
        Long taskId;
        try { taskId = Long.valueOf(objectId); } catch (RuntimeException ex) { return denied(); }
        PlatformExportTaskDO task = taskMapper.selectByActor(new ExportTaskActorQuery(tenantId, actorId, taskId));
        if (task == null || !PURPOSE.equals(purpose) || !ACTIONS.contains(action)
                || !("export-task-" + taskId).equals(referenceKey)
                || (expectedScopeVersion != null && !expectedScopeVersion.equals(task.getScopeVersion()))) {
            return denied();
        }
        boolean write = FileActionCodes.UPLOAD.equals(action);
        boolean read = FileActionCodes.READ.equals(action) || FileActionCodes.DOWNLOAD.equals(action);
        boolean invalidate = FileActionCodes.INVALIDATE.equals(action);
        if ((write && !"GENERATING".equals(task.getTaskStatus()))
                || (read && !"SUCCEEDED".equals(task.getTaskStatus()))
                || (invalidate && !"SUCCEEDED".equals(task.getTaskStatus()))) return denied();
        return new FileBusinessObjectPolicyFact(true, task.getScopeVersion(), "IMMUTABLE", "SINGLE",
                Set.of(PURPOSE), Set.of("text/csv"), 52_428_800L, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, "IMMUTABLE", "SINGLE",
                Set.of(), Set.of(), 0L, "INTERNAL");
    }
}
