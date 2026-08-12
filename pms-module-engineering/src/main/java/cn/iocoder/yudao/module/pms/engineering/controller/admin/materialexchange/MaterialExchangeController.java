package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import cn.iocoder.yudao.module.pms.engineering.service.materialexchange.MaterialExchangeService;
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
 * 管理后台 - PMS 物料换货协同 Controller（FR-ENG-003）。
 * <p>
 * 路径前缀 {@code /pms/eng-material-exch}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-material-exch:*}。
 */
@Tag(name = "管理后台 - PMS 物料换货协同")
@RestController
@RequestMapping("/pms/eng-material-exch")
@Validated
public class MaterialExchangeController {

    @Resource
    private MaterialExchangeService materialExchangeService;

    @PostMapping("/create")
    @Operation(summary = "创建换货协同单")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:create')")
    public CommonResult<Long> createMaterialExchange(@Valid @RequestBody MaterialExchangeSaveReqVO createReqVO) {
        return success(materialExchangeService.createMaterialExchange(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新换货协同单")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:update')")
    public CommonResult<Boolean> updateMaterialExchange(@Valid @RequestBody MaterialExchangeSaveReqVO updateReqVO) {
        materialExchangeService.updateMaterialExchange(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除换货协同单")
    @Parameter(name = "id", description = "换货协同单编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:delete')")
    public CommonResult<Boolean> deleteMaterialExchange(@RequestParam("id") Long id) {
        materialExchangeService.deleteMaterialExchange(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询换货协同单详情")
    @Parameter(name = "id", description = "换货协同单编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:query')")
    public CommonResult<MaterialExchangeRespVO> getMaterialExchange(@RequestParam("id") Long id) {
        MaterialExchangeDO entity = materialExchangeService.getMaterialExchange(id);
        return success(BeanUtils.toBean(entity, MaterialExchangeRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询换货协同单")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:query')")
    public CommonResult<PageResult<MaterialExchangeRespVO>> getMaterialExchangePage(@Validated MaterialExchangePageReqVO pageReqVO) {
        PageResult<MaterialExchangeDO> pageResult = materialExchangeService.getMaterialExchangePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MaterialExchangeRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交换货协同单（0 草稿 / 4 已驳回 → 1 已提交）")
    @Parameter(name = "id", description = "换货协同单编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:update')")
    public CommonResult<Boolean> submitMaterialExchange(@RequestParam("id") Long id) {
        materialExchangeService.submitMaterialExchange(id);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审批换货协同单（1 已提交 / 2 审批中 → 3/4/0/2）")
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:update')")
    public CommonResult<Boolean> approveMaterialExchange(@Valid @RequestBody MaterialExchangeApproveReqVO reqVO) {
        materialExchangeService.approveMaterialExchange(reqVO);
        return success(true);
    }

    @PutMapping("/withdraw")
    @Operation(summary = "撤回换货协同单（1 已提交 / 2 审批中 → 5 已撤回）")
    @Parameter(name = "id", description = "换货协同单编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:update')")
    public CommonResult<Boolean> withdrawMaterialExchange(@RequestParam("id") Long id) {
        materialExchangeService.withdrawMaterialExchange(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "终止换货协同单（非 3 已通过 / 非 6 已终止 → 6 已终止）")
    @Parameter(name = "id", description = "换货协同单编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:update')")
    public CommonResult<Boolean> terminateMaterialExchange(@RequestParam("id") Long id) {
        materialExchangeService.terminateMaterialExchange(id);
        return success(true);
    }

    @PutMapping("/push-crm")
    @Operation(summary = "推送 CRM（仅 crm_push_status=PENDING 可推送；传入 crmOrderNo 则直接置为 RECEIVED）")
    @Parameter(name = "id", description = "换货协同单编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-material-exch:update')")
    public CommonResult<Boolean> pushCrm(@RequestParam("id") Long id,
                                         @RequestParam(value = "crmOrderNo", required = false) String crmOrderNo) {
        materialExchangeService.pushToCrm(id, crmOrderNo);
        return success(true);
    }
}
