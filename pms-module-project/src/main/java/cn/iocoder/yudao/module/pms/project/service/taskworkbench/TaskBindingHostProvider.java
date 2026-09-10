package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

public interface TaskBindingHostProvider {
    String bindingType();
    default java.util.Set<String> bindingTypes() { return java.util.Set.of(bindingType()); }
    TaskBindingInspection inspect(TaskBindingInspectionQuery query);
}
