package cn.iocoder.yudao.module.pms.project.controller.admin.batchchange;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangeItemRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangeRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeItemDO;
import cn.iocoder.yudao.module.pms.project.service.batchchange.TeamBatchChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 团队批量变更 Controller（FR-PROJ-014）。
 * <p>
 * 路径前缀 {@code /pms/batch-change}，对应菜单权限 {@code pms:team-batch-change:*}。
 * 提供批量变更批次 CRUD、明细查询与执行能力，执行时逐条返回结果。
 */
@Tag(name = "管理后台 - PMS 团队批量变更")
@RestController
@RequestMapping("/pms/batch-change")
@Validated
public class TeamBatchChangeController {

    @Resource
    private TeamBatchChangeService teamBatchChangeService;

    @PostMapping("/create")
    @Operation(summary = "创建批量变更批次（生成明细）")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:create')")
    public CommonResult<Long> createBatchChange(@Valid @RequestBody TeamBatchChangeSaveReqVO createReqVO) {
        return success(teamBatchChangeService.createBatchChange(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新批量变更批次")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:update')")
    public CommonResult<Boolean> updateBatchChange(@Valid @RequestBody TeamBatchChangeSaveReqVO updateReqVO) {
        teamBatchChangeService.updateBatchChange(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除批量变更批次")
    @Parameter(name = "id", description = "批次编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:delete')")
    public CommonResult<Boolean> deleteBatchChange(@RequestParam("id") Long id) {
        teamBatchChangeService.deleteBatchChange(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得批量变更批次详情（含明细）")
    @Parameter(name = "id", description = "批次编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:query')")
    public CommonResult<TeamBatchChangeRespVO> getBatchChange(@RequestParam("id") Long id) {
        TeamBatchChangeDO batch = teamBatchChangeService.getBatchChange(id);
        TeamBatchChangeRespVO respVO = BeanUtils.toBean(batch, TeamBatchChangeRespVO.class);
        if (respVO != null) {
            List<TeamBatchChangeItemDO> items = teamBatchChangeService.getBatchChangeItems(id);
            respVO.setItems(BeanUtils.toBean(items, TeamBatchChangeItemRespVO.class));
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得批量变更批次分页")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:query')")
    public CommonResult<PageResult<TeamBatchChangeRespVO>> getBatchChangePage(
            @Validated TeamBatchChangePageReqVO pageReqVO) {
        PageResult<TeamBatchChangeDO> pageResult = teamBatchChangeService.getBatchChangePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TeamBatchChangeRespVO.class));
    }

    @GetMapping("/items")
    @Operation(summary = "查询批次明细列表")
    @Parameter(name = "batchId", description = "批次编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:query')")
    public CommonResult<List<TeamBatchChangeItemRespVO>> getBatchChangeItems(
            @RequestParam("batchId") Long batchId) {
        List<TeamBatchChangeItemDO> list = teamBatchChangeService.getBatchChangeItems(batchId);
        return success(BeanUtils.toBean(list, TeamBatchChangeItemRespVO.class));
    }

    @PostMapping("/execute")
    @Operation(summary = "执行批量变更（逐条返回结果）")
    @Parameter(name = "id", description = "批次编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:team-batch-change:execute')")
    public CommonResult<List<TeamBatchChangeItemRespVO>> executeBatchChange(@RequestParam("id") Long id) {
        List<TeamBatchChangeItemDO> items = teamBatchChangeService.executeBatchChange(id);
        return success(BeanUtils.toBean(items, TeamBatchChangeItemRespVO.class));
    }

}
