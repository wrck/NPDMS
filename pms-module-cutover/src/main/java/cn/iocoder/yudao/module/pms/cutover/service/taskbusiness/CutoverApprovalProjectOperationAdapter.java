package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.module.pms.cutover.api.task.CutoverTaskIntakeApiImpl;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 审批投影使用同一任务身份，创建和授权复用 CUT 原生项目接入。 */
@Component
@RequiredArgsConstructor
public class CutoverApprovalProjectOperationAdapter implements ProjectBusinessOperationProvider,
        ProjectBusinessOperationCommandAdapter, ProjectBusinessOperationAccessProvider {
    public static final String CREATE = "CUT.CUTOVER_APPROVAL.CREATE";
    private final CutoverProjectOperationAdapter tasks;
    @Override public String ownerContext() { return "CUT"; }
    @Override public String objectType() { return "CUTOVER_APPROVAL"; }
    @Override public List<ProjectBusinessOperationDescriptor> operations() {
        return List.of(new ProjectBusinessOperationDescriptor(CREATE, 1, ownerContext(), objectType(), "发起项目割接", "CREATE",
                Set.of("PRE", "POST"), CutoverTaskIntakeApiImpl.class, "create"));
    }
    @Override public Map<String, ProjectOperationControlScope> controlScopes() { return Map.of(CREATE, ProjectOperationControlScope.PROJECT_ENTRY_ONLY); }
    @Override public Map<String, String> permissionCodes() { return Map.of(CREATE, "pms:cutover-task:create"); }
    @Override public boolean supports(String code, int version) { return CREATE.equals(code) && version == 1; }
    @Override public Access inspect(Context context) {
        var access = tasks.inspect(context);
        return new Access(access.permittedOperations().contains(CutoverProjectOperationAdapter.CREATE) ? Set.of(CREATE) : Set.of(),
                access.objectFactVersion());
    }
    @Override public void authorizeReplay(String code, ProjectOperationCommand command) {
        requireCode(code);
        tasks.authorizeReplay(CutoverProjectOperationAdapter.CREATE, command);
    }
    @Override public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        requireCode(code);
        var result = tasks.invoke(CutoverProjectOperationAdapter.CREATE, command);
        return new ProjectOperationResult(ownerContext(), objectType(), result.objectId(), result.revisionId(),
                result.objectVersion(), result.businessFactVersion(), result.resultCode(), result.response(), result.replayed());
    }
    private void requireCode(String code) {
        if (!supports(code, 1)) throw new SecurityException("无效的项目割接审批操作");
    }
}
