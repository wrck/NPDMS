package cn.iocoder.yudao.module.pms.asset.controller.admin.location;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.*;
import cn.iocoder.yudao.module.pms.asset.service.location.AssetLocationAdminService;
import cn.iocoder.yudao.module.pms.asset.service.location.SiteLocationTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 资产地点")
@RestController
@RequestMapping("/pms/asset-locations")
@Validated
@RequiredArgsConstructor
public class AssetLocationController {

    private final AssetLocationApi assetLocationApi;
    private final SiteLocationTreeService siteLocationTreeService;
    private final AssetLocationAdminService assetLocationAdminService;

    @PostMapping("/maintain")
    @Operation(summary = "维护地址、站点或站点内部位置")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:update')")
    public CommonResult<LocationReferenceDTO> maintain(@Valid @RequestBody LocationMaintainReqVO reqVO) {
        return success(assetLocationApi.maintain(reqVO.toCommand()));
    }

    @GetMapping("/addresses/get")
    @Operation(summary = "查询地址")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<AddressRespDTO> getAddress(@RequestParam("id") Long id,
                                                    @RequestParam(value = "version", required = false) Integer version) {
        return success(assetLocationApi.getAddress(id, version));
    }

    @GetMapping("/addresses/page")
    @Operation(summary = "分页查询地址")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<PageResult<AddressRespDTO>> getAddressPage(@Valid AddressPageReqVO reqVO) {
        return success(assetLocationAdminService.getAddressPage(reqVO));
    }

    @GetMapping("/sites/get")
    @Operation(summary = "查询站点")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<SiteRespDTO> getSite(@RequestParam("id") Long id,
                                              @RequestParam(value = "version", required = false) Integer version) {
        return success(assetLocationApi.getSite(id, version));
    }

    @GetMapping("/sites/page")
    @Operation(summary = "分页查询站点")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<PageResult<SiteRespDTO>> getSitePage(@Valid SitePageReqVO reqVO) {
        return success(assetLocationAdminService.getSitePage(reqVO));
    }

    @GetMapping("/sites/tree")
    @Operation(summary = "查询站点任意深度位置树")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<List<SiteLocationRespDTO>> getSiteLocationTree(@RequestParam("siteId") Long siteId) {
        return success(assetLocationApi.getLocationTree(siteId));
    }

    @PutMapping("/sites/tree/disable")
    @Operation(summary = "停用站点位置节点")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:update')")
    public CommonResult<Boolean> disableSiteLocation(@Valid @RequestBody SiteLocationDisableReqVO reqVO) {
        siteLocationTreeService.disable(reqVO.getId(), reqVO.getVersion());
        return success(true);
    }

    @GetMapping("/area-department-mappings/resolve")
    @Operation(summary = "按行政区划编码和层级精确解析服务办事处")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<AreaDepartmentMappingRespDTO> resolveDepartment(
            @RequestParam("areaCode") String areaCode, @RequestParam("areaLevel") String areaLevel) {
        return success(assetLocationApi.resolveDepartment(areaCode, areaLevel));
    }

    @GetMapping("/area-department-mappings/page")
    @Operation(summary = "分页查询行政区划与服务办事处映射")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:query')")
    public CommonResult<PageResult<AreaDepartmentMappingRespDTO>> getAreaDepartmentMappingPage(
            @Valid AreaDepartmentMappingPageReqVO reqVO) {
        return success(assetLocationAdminService.getMappingPage(reqVO));
    }

    @PostMapping("/area-department-mappings/save")
    @Operation(summary = "创建或修订行政区划与服务办事处映射")
    @PreAuthorize("@ss.hasPermission('pms:asset-location:update')")
    public CommonResult<Long> saveAreaDepartmentMapping(
            @Valid @RequestBody AreaDepartmentMappingSaveReqVO reqVO) {
        return success(assetLocationAdminService.saveMapping(reqVO));
    }

}
