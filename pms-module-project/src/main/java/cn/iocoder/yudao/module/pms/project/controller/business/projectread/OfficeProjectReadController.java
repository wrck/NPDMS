package cn.iocoder.yudao.module.pms.project.controller.business.projectread;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.OfficeProjectSearchReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.service.projectscope.OfficeProjectSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/projects")
public class OfficeProjectReadController {
    private final OfficeProjectSearchService search;
    @GetMapping("/office-readonly-search")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PageResult<ProjectRespVO>> search(@Valid OfficeProjectSearchReqVO request) {
        return CommonResult.success(BeanUtils.toBean(search.search(request), ProjectRespVO.class));
    }
}
