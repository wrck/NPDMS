package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** 新工作台独立注册，已冻结旧页面注册继续保留。 */
@Component
@RequiredArgsConstructor
public class CutoverWorkbenchBusinessViewProvider implements BusinessViewComponentProvider {
    private final CutoverCompletionBusinessViewProvider historical;
    @Override public Component component() {
        return new Component("CUTOVER_TASK", "CUT", ViewSource.PAGE, "CUT_CUTOVER_WORKBENCH", "1",
                historical.component().contextSchema(), JsonUtils.parseTree("[\"QUERY\",\"CREATE\"]"),
                "CUT_CUTOVER_WORKBENCH_QUERY", "CUT_CUTOVER_WORKBENCH_COMMAND", "CUT_CUTOVER_WORKBENCH_PERMISSION", "割接工作台");
    }
    @Override public Set<String> pagePaths() { return Set.of("/pms/cutover/workbench"); }
    @Override public boolean canConfigure(Context context, ConfigurationAction action) { return historical.canConfigure(context, action); }
    @Override public Dependencies validateConfiguration(Context context, Long form, ValidationMode mode) { return historical.validateConfiguration(context, form, mode); }
}
