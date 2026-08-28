package cn.iocoder.yudao.module.system.controller.admin.company;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanyRespVO;
import cn.iocoder.yudao.module.system.controller.admin.company.vo.CompanySaveReqVO;
import cn.iocoder.yudao.module.system.service.company.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 公司主数据")
@RestController
@RequestMapping("/system/companies")
@Validated
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/page")
    @Operation(summary = "分页查询公司")
    @PreAuthorize("@ss.hasPermission('system:company:query')")
    public CommonResult<PageResult<CompanyRespVO>> getCompanyPage(@Valid CompanyPageReqVO reqVO) {
        return success(BeanUtils.toBean(companyService.getCompanyPage(reqVO), CompanyRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "查询公司")
    @PreAuthorize("@ss.hasPermission('system:company:query')")
    public CommonResult<CompanyRespVO> getCompany(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(companyService.getCompany(id), CompanyRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "查询启用公司列表")
    public CommonResult<List<CompanyRespVO>> getSimpleCompanyList() {
        return success(BeanUtils.toBean(companyService.getEnabledCompanyList(), CompanyRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建公司")
    @PreAuthorize("@ss.hasPermission('system:company:create')")
    public CommonResult<Long> createCompany(@Valid @RequestBody CompanySaveReqVO reqVO) {
        return success(companyService.createCompany(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修订公司")
    @PreAuthorize("@ss.hasPermission('system:company:update')")
    public CommonResult<Boolean> updateCompany(@Valid @RequestBody CompanySaveReqVO reqVO) {
        companyService.updateCompany(reqVO);
        return success(true);
    }

}
