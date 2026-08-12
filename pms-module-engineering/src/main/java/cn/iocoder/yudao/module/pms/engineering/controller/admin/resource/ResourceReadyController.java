package cn.iocoder.yudao.module.pms.engineering.controller.admin.resource;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadyRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.resource.ResourceReadyDO;
import cn.iocoder.yudao.module.pms.engineering.service.resource.ResourceReadyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 资源与备件就绪 Controller（FR-ENG-018）。
 * <p>
 * 路径前缀 {@code /pms/eng-resource}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-resource:*}。
 */
@Tag(name = "管理后台 - PMS 资源与备件就绪")
@RestController
@RequestMapping("/pms/eng-resource")
@Validated
public class ResourceReadyController {

    @Resource
    private ResourceReadyService resourceReadyService;

    @PostMapping("/create")
    @Operation(summary = "创建资源就绪记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:create')")
    public CommonResult<Long> createResourceReady(@Valid @RequestBody ResourceReadySaveReqVO createReqVO) {
        return success(resourceReadyService.createResourceReady(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新资源就绪记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:update')")
    public CommonResult<Boolean> updateResourceReady(@Valid @RequestBody ResourceReadySaveReqVO updateReqVO) {
        resourceReadyService.updateResourceReady(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除资源就绪记录")
    @Parameter(name = "id", description = "资源编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:delete')")
    public CommonResult<Boolean> deleteResourceReady(@RequestParam("id") Long id) {
        resourceReadyService.deleteResourceReady(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询资源就绪详情")
    @Parameter(name = "id", description = "资源编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:query')")
    public CommonResult<ResourceReadyRespVO> getResourceReady(@RequestParam("id") Long id) {
        ResourceReadyDO entity = resourceReadyService.getResourceReady(id);
        return success(BeanUtils.toBean(entity, ResourceReadyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询资源就绪记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:query')")
    public CommonResult<PageResult<ResourceReadyRespVO>> getResourceReadyPage(@Validated ResourceReadyPageReqVO pageReqVO) {
        PageResult<ResourceReadyDO> pageResult = resourceReadyService.getResourceReadyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ResourceReadyRespVO.class));
    }

    @PutMapping("/mark-ready")
    @Operation(summary = "标记就绪（0未就绪 → 1已就绪）")
    @Parameter(name = "id", description = "资源编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:update')")
    public CommonResult<Boolean> markReady(@RequestParam("id") Long id) {
        resourceReadyService.markReady(id);
        return success(true);
    }

    @PutMapping("/mark-abnormal")
    @Operation(summary = "标记异常（0未就绪 / 1已就绪 → 2异常）")
    @Parameter(name = "id", description = "资源编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:update')")
    public CommonResult<Boolean> markAbnormal(@RequestParam("id") Long id) {
        resourceReadyService.markAbnormal(id);
        return success(true);
    }

    @PutMapping("/reset-not-ready")
    @Operation(summary = "重置为未就绪（1已就绪 / 2异常 → 0未就绪）")
    @Parameter(name = "id", description = "资源编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-resource:update')")
    public CommonResult<Boolean> resetToNotReady(@RequestParam("id") Long id) {
        resourceReadyService.resetToNotReady(id);
        return success(true);
    }
}
