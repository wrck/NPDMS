package cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement.AnnouncementDO;
import cn.iocoder.yudao.module.pms.engineering.service.announcement.AnnouncementService;
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
 * 管理后台 - PMS 技术公告 Controller（FR-ENG-009）。
 * <p>
 * 路径前缀 {@code /pms/eng-announcement}，对应菜单权限 {@code pms:eng-announcement:*}。
 */
@Tag(name = "管理后台 - PMS 技术公告")
@RestController
@RequestMapping("/pms/eng-announcement")
@Validated
public class AnnouncementController {

    @Resource
    private AnnouncementService announcementService;

    @PostMapping("/create")
    @Operation(summary = "创建技术公告")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:create')")
    public CommonResult<Long> createAnnouncement(@Valid @RequestBody AnnouncementSaveReqVO createReqVO) {
        return success(announcementService.createAnnouncement(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新技术公告")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:update')")
    public CommonResult<Boolean> updateAnnouncement(@Valid @RequestBody AnnouncementSaveReqVO updateReqVO) {
        announcementService.updateAnnouncement(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除技术公告")
    @Parameter(name = "id", description = "公告ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:delete')")
    public CommonResult<Boolean> deleteAnnouncement(@RequestParam("id") Long id) {
        announcementService.deleteAnnouncement(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询技术公告详情")
    @Parameter(name = "id", description = "公告ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:query')")
    public CommonResult<AnnouncementRespVO> getAnnouncement(@RequestParam("id") Long id) {
        AnnouncementDO entity = announcementService.getAnnouncement(id);
        return success(BeanUtils.toBean(entity, AnnouncementRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询技术公告")
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:query')")
    public CommonResult<PageResult<AnnouncementRespVO>> getAnnouncementPage(@Validated AnnouncementPageReqVO pageReqVO) {
        PageResult<AnnouncementDO> pageResult = announcementService.getAnnouncementPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AnnouncementRespVO.class));
    }

    @PutMapping("/publish")
    @Operation(summary = "发布技术公告（0 草稿 → 1 已发布）")
    @Parameter(name = "id", description = "公告ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:publish')")
    public CommonResult<Boolean> publishAnnouncement(@RequestParam("id") Long id) {
        announcementService.publishAnnouncement(id);
        return success(true);
    }

    @PutMapping("/disable")
    @Operation(summary = "停用技术公告（1 已发布 → 2 已停用）")
    @Parameter(name = "id", description = "公告ID", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-announcement:disable')")
    public CommonResult<Boolean> disableAnnouncement(@RequestParam("id") Long id) {
        announcementService.disableAnnouncement(id);
        return success(true);
    }
}
