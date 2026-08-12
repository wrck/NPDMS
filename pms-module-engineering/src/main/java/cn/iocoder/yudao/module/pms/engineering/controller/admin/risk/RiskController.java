package cn.iocoder.yudao.module.pms.engineering.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskHandleReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.risk.vo.RiskSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.risk.RiskDO;
import cn.iocoder.yudao.module.pms.engineering.service.risk.RiskService;
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
 * 管理后台 - PMS 单机风险 Controller（FR-ENG-008）。
 * <p>
 * 路径前缀 {@code /pms/eng-risk}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-risk:*}。
 */
@Tag(name = "管理后台 - PMS 单机风险")
@RestController
@RequestMapping("/pms/eng-risk")
@Validated
public class RiskController {

    @Resource
    private RiskService riskService;

    @PostMapping("/create")
    @Operation(summary = "创建单机风险")
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:create')")
    public CommonResult<Long> createRisk(@Valid @RequestBody RiskSaveReqVO createReqVO) {
        return success(riskService.createRisk(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新单机风险")
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:update')")
    public CommonResult<Boolean> updateRisk(@Valid @RequestBody RiskSaveReqVO updateReqVO) {
        riskService.updateRisk(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除单机风险")
    @Parameter(name = "id", description = "风险ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:delete')")
    public CommonResult<Boolean> deleteRisk(@RequestParam("id") Long id) {
        riskService.deleteRisk(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询单机风险详情")
    @Parameter(name = "id", description = "风险ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:query')")
    public CommonResult<RiskRespVO> getRisk(@RequestParam("id") Long id) {
        RiskDO entity = riskService.getRisk(id);
        return success(BeanUtils.toBean(entity, RiskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询单机风险")
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:query')")
    public CommonResult<PageResult<RiskRespVO>> getRiskPage(@Validated RiskPageReqVO pageReqVO) {
        PageResult<RiskDO> pageResult = riskService.getRiskPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RiskRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认风险（0 草稿 / 1 已识别 → 2 已确认）")
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:confirm')")
    public CommonResult<Boolean> confirmRisk(@Valid @RequestBody RiskHandleReqVO reqVO) {
        riskService.confirmRisk(reqVO);
        return success(true);
    }

    @PutMapping("/sync-crm")
    @Operation(summary = "同步CRM（2 已确认 → 3 已同步CRM）")
    @Parameter(name = "id", description = "风险ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:sync')")
    public CommonResult<Boolean> syncCrm(@RequestParam("id") Long id) {
        riskService.syncCrm(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭风险（3 已同步CRM → 4 已关闭）")
    @PreAuthorize("@ss.hasPermission('pms:eng-risk:close')")
    public CommonResult<Boolean> closeRisk(@Valid @RequestBody RiskHandleReqVO reqVO) {
        riskService.closeRisk(reqVO);
        return success(true);
    }
}
