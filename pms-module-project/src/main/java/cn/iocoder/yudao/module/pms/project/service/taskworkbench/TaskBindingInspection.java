package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import java.util.Set;

public record TaskBindingInspection(
        String bindingType,
        Set<String> allowedActions,
        String factVersion,
        String recoverableError) {

    public static TaskBindingInspection failed(String bindingType, String error) {
        return new TaskBindingInspection(bindingType, Set.of(), null, error);
    }
}
