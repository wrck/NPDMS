package cn.iocoder.yudao.module.pms.platform.controller.admin.business;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessViews;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
/** Read-only deployed route discovery; business APIs retain all real permission and scope checks. */
@RestController @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/pms/business-defaults")
public class ProjectBusinessViewController {
    private final DirectBusinessViews views;
    @GetMapping("/{code}/view") public CommonResult<DirectBusinessViews.View> view(@PathVariable String code){return success(views.view(code));}
}
