package cn.iocoder.yudao;

import cn.iocoder.yudao.module.pms.cutover.service.taskbusiness.CutoverApprovalProjectOperationAdapter;
import cn.iocoder.yudao.module.pms.cutover.service.taskbusiness.CutoverProjectOperationAdapter;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationAdapters;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** Runs against the frozen server libraries without connecting to business infrastructure. */
public class CutoverOperationAssemblyRegression {
    public static void main(String[] args) {
        verify(1, false, true);
        verify(0, false, false);
        verify(2, false, false);
        verify(1, true, false);
        System.out.println("Cutover operation assembly: 8 availability checks passed");
    }

    private static void verify(int sinkCount, boolean duplicateCommands, boolean expected) {
        var beans = new DefaultListableBeanFactory();
        var tasks = new CutoverProjectOperationAdapter(null, null, null, null);
        var approvals = new CutoverApprovalProjectOperationAdapter(tasks);
        beans.registerSingleton("tasks", tasks);
        beans.registerSingleton("approvals", approvals);
        if (duplicateCommands) {
            beans.registerSingleton("duplicateTasks", new CutoverProjectOperationAdapter(null, null, null, null));
            beans.registerSingleton("duplicateApprovals", new CutoverApprovalProjectOperationAdapter(tasks));
        }
        for (int i = 0; i < sinkCount; i++) {
            ProjectOperationResultSink sink = (code, version, command, result, tenant, actor, correlation) -> { };
            beans.registerSingleton("sink" + i, sink);
        }
        beans.registerSingleton("runtime", new ProjectOperationAdapters(
                beans.getBeanProvider(ProjectBusinessOperationCommandAdapter.class),
                beans.getBeanProvider(ProjectOperationResultSink.class)));
        var registry = new ProjectBusinessOperationRegistry(java.util.List.of(tasks, approvals),
                beans.getBeanProvider(ProjectOperationRuntimeCapability.class).orderedStream().toList());
        for (String code : java.util.List.of(CutoverProjectOperationAdapter.CREATE, CutoverApprovalProjectOperationAdapter.CREATE)) {
            if (registry.runtimeAvailable(code, 1) != expected)
                throw new AssertionError(code + " availability mismatch: sinks=" + sinkCount + ", duplicateCommands=" + duplicateCommands);
        }
    }
}
