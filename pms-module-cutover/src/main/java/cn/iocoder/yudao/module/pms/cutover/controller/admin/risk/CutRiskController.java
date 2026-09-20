package cn.iocoder.yudao.module.pms.cutover.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskRetiredDO;
import cn.iocoder.yudao.module.pms.cutover.service.risk.CutRiskService;
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
 * 管理后台 - PMS 割接风险 Controller（FR-CUT-004 / FR-CUT-006）。
 * <p>
 * 路径前缀 {@code /pms/cut-risk}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:cut-risk:*}。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Tag(name = "管理后台 - PMS 割接风险")
@RestController
@RequestMapping("/pms/cut-risk")
@Validated
@Deprecated
public class CutRiskController {

    @Resource
    private CutRiskService cutRiskService;

    @PostMapping("/create")
    @Operation(summary = "创建割接风险")
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:create')")
    public CommonResult<Long> createCutRisk(@Valid @RequestBody CutRiskSaveReqVO createReqVO) {
        return success(cutRiskService.createCutRiskRetired(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新割接风险")
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:update')")
    public CommonResult<Boolean> updateCutRisk(@Valid @RequestBody CutRiskSaveReqVO updateReqVO) {
        cutRiskService.updateCutRiskRetired(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除割接风险")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:delete')")
    public CommonResult<Boolean> deleteCutRisk(@RequestParam("id") Long id) {
        cutRiskService.deleteCutRiskRetired(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询割接风险详情")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:query')")
    public CommonResult<CutRiskRespVO> getCutRisk(@RequestParam("id") Long id) {
        CutRiskRetiredDO entity = cutRiskService.getCutRiskRetired(id);
        return success(BeanUtils.toBean(entity, CutRiskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询割接风险")
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:query')")
    public CommonResult<PageResult<CutRiskRespVO>> getCutRiskPage(@Validated CutRiskPageReqVO pageReqVO) {
        PageResult<CutRiskRetiredDO> pageResult = cutRiskService.getCutRiskPageRetired(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CutRiskRespVO.class));
    }

    @PutMapping("/start-process")
    @Operation(summary = "开始处理（0待处理 → 1处理中）")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:update')")
    public CommonResult<Boolean> startProcess(@RequestParam("id") Long id) {
        cutRiskService.startProcessRetired(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "闭环（1处理中 → 2已闭环）")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:update')")
    public CommonResult<Boolean> close(@RequestParam("id") Long id) {
        cutRiskService.closeRetired(id);
        return success(true);
    }

    @PutMapping("/suspend")
    @Operation(summary = "挂起（0待处理/1处理中 → 3已挂起）")
    @Parameter(name = "id", description = "风险编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:cut-risk:update')")
    public CommonResult<Boolean> suspend(@RequestParam("id") Long id) {
        cutRiskService.suspendRetired(id);
        return success(true);
    }
}
