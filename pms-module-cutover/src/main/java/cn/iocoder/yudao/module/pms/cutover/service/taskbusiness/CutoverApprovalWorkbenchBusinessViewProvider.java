package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** 新计划可选办理入口；保留历史只读审批注册。 */
@Component
@RequiredArgsConstructor
public class CutoverApprovalWorkbenchBusinessViewProvider implements BusinessViewComponentProvider {
    private final CutoverApprovalBusinessViewProvider historical;
    @Override public Component component() {
        return new Component("CUTOVER_APPROVAL", "CUT", ViewSource.PAGE, "CUT_CUTOVER_APPROVAL_WORKBENCH", "1",
                historical.component().contextSchema(), JsonUtils.parseTree("[\"QUERY\",\"CREATE\"]"),
                "CUT_CUTOVER_APPROVAL_WORKBENCH_QUERY", "CUT_CUTOVER_APPROVAL_WORKBENCH_COMMAND",
                "CUT_CUTOVER_APPROVAL_WORKBENCH_PERMISSION", "项目割接审批办理");
    }
    @Override public Set<String> pagePaths() { return Set.of("/pms/cutover/approval-workbench"); }
    @Override public boolean canConfigure(Context context, ConfigurationAction action) { return historical.canConfigure(context, action); }
    @Override public Dependencies validateConfiguration(Context context, Long form, ValidationMode mode) { return historical.validateConfiguration(context, form, mode); }
}
