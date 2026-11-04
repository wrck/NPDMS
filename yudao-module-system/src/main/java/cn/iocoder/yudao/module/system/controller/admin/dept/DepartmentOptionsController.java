package cn.iocoder.yudao.module.system.controller.admin.dept;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSimpleRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DepartmentOptionsReqVO;
import cn.iocoder.yudao.module.system.service.dept.EnabledDepartmentOptionsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/department-options")
public class DepartmentOptionsController {
    private final EnabledDepartmentOptionsService departments;

    @GetMapping
    @Operation(summary = "按多个编码前缀查询启用部门选项")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<DeptSimpleRespVO>> getOptions(DepartmentOptionsReqVO request) {
        return CommonResult.success(BeanUtils.toBean(departments.getOptions(request), DeptSimpleRespVO.class));
    }
}
