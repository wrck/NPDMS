package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/** 复用根项目创建字段；客户只能由编码选择，名称与ID由CUS回源。 */
public class SelectedCustomerProjectCreateReqVO extends ProjectCreateReqVO {
    @Override
    @NotBlank(message = "请选择客户主档")
    @Size(max = 64)
    public String getCustomerCode() {
        return super.getCustomerCode();
    }

    @Override
    @Null(message = "客户名称由主档读取，请勿手填")
    public String getCustomerName() {
        return super.getCustomerName();
    }

    @Override
    @Null(message = "子项目请通过项目拆分入口创建")
    public Long getParentId() {
        return super.getParentId();
    }
}
