package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckHandleReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcementcheck.AnnouncementCheckDO;
import cn.iocoder.yudao.module.pms.engineering.service.announcementcheck.AnnouncementCheckService;
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
 * 管理后台 - PMS 公告预检查 Controller（FR-ENG-009）。
 * <p>
 * 路径前缀 {@code /pms/eng-announcement-check}，对应菜单权限 {@code pms:eng-announcement-check:*}。
 */
@Tag(name = "管理后台 - PMS 公告预检查")
@RestController
@RequestMapping("/pms/eng-announcement-check")
@Validated
public class AnnouncementCheckController {

    @Resource
    private AnnouncementCheckService announcementCheckService;

    @PostMapping("/create")
    @Operation(summary = "创建公告预检查记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:create')")
    public CommonResult<Long> createAnnouncementCheck(@Valid @RequestBody AnnouncementCheckSaveReqVO createReqVO) {
        return success(announcementCheckService.createAnnouncementCheck(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新公告预检查记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:update')")
    public CommonResult<Boolean> updateAnnouncementCheck(@Valid @RequestBody AnnouncementCheckSaveReqVO updateReqVO) {
        announcementCheckService.updateAnnouncementCheck(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公告预检查记录")
    @Parameter(name = "id", description = "检查记录ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:delete')")
    public CommonResult<Boolean> deleteAnnouncementCheck(@RequestParam("id") Long id) {
        announcementCheckService.deleteAnnouncementCheck(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询公告预检查记录详情")
    @Parameter(name = "id", description = "检查记录ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:query')")
    public CommonResult<AnnouncementCheckRespVO> getAnnouncementCheck(@RequestParam("id") Long id) {
        AnnouncementCheckDO entity = announcementCheckService.getAnnouncementCheck(id);
        return success(BeanUtils.toBean(entity, AnnouncementCheckRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询公告预检查记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:query')")
    public CommonResult<PageResult<AnnouncementCheckRespVO>> getAnnouncementCheckPage(@Validated AnnouncementCheckPageReqVO pageReqVO) {
        PageResult<AnnouncementCheckDO> pageResult = announcementCheckService.getAnnouncementCheckPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AnnouncementCheckRespVO.class));
    }

    @PutMapping("/perform-check")
    @Operation(summary = "执行检查（0 待检查 → 1 已检查）")
    @Parameter(name = "id", description = "检查记录ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:update')")
    public CommonResult<Boolean> performCheck(@RequestParam("id") Long id) {
        announcementCheckService.performCheck(id);
        return success(true);
    }

    @PutMapping("/handle")
    @Operation(summary = "处置检查记录（1 已检查 → 2 已处置 / 3 已忽略）")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement-check:handle')")
    public CommonResult<Boolean> handleCheck(@Valid @RequestBody AnnouncementCheckHandleReqVO reqVO) {
        announcementCheckService.handleCheck(reqVO);
        return success(true);
    }
}
