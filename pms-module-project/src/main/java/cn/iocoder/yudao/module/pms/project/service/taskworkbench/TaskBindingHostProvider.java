package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

public interface TaskBindingHostProvider {
    String bindingType();
    TaskBindingInspection inspect(TaskBindingInspectionQuery query);
}
