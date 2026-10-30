package cn.iocoder.yudao.module.pms.engineering.controller.admin.training;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingIssueRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService;
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
 * 管理后台 - PMS 现场培训记录 Controller（ACC-01，Demo 6.1）。
 * <p>
 * 路径前缀 {@code /pms/imp-training}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:imp-training:*}。客户确认走公开令牌接口
 * {@link TrainingPublicController}。
 */
@Tag(name = "管理后台 - PMS 现场培训记录")
@RestController
@RequestMapping("/pms/imp-training")
@Validated
public class TrainingController {

    @Resource
    private TrainingService trainingService;

    @PostMapping("/create")
    @Operation(summary = "创建培训记录（草稿）")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:create')")
    public CommonResult<Long> createTraining(@Valid @RequestBody TrainingSaveReqVO createReqVO) {
        return success(trainingService.createTraining(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新培训记录（仅草稿可修改）")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:update')")
    public CommonResult<Boolean> updateTraining(@Valid @RequestBody TrainingSaveReqVO updateReqVO) {
        trainingService.updateTraining(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除培训记录（仅草稿可删除）")
    @Parameter(name = "id", description = "培训记录编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-training:delete')")
    public CommonResult<Boolean> deleteTraining(@RequestParam("id") Long id) {
        trainingService.deleteTraining(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询培训记录详情")
    @Parameter(name = "id", description = "培训记录编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public CommonResult<TrainingRespVO> getTraining(@RequestParam("id") Long id) {
        TrainingDO entity = trainingService.getTraining(id);
        return success(BeanUtils.toBean(entity, TrainingRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询培训记录")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public CommonResult<PageResult<TrainingRespVO>> getTrainingPage(@Validated TrainingPageReqVO pageReqVO) {
        PageResult<TrainingDO> pageResult = trainingService.getTrainingPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TrainingRespVO.class));
    }

    @PutMapping("/issue")
    @Operation(summary = "外发培训记录（生成客户确认链接与培训记录表文件）")
    @Parameter(name = "id", description = "培训记录编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-training:issue')")
    public CommonResult<TrainingIssueRespVO> issueTraining(@RequestParam("id") Long id) {
        return success(trainingService.issueTraining(id));
    }

    @PutMapping("/void")
    @Operation(summary = "作废培训记录（草稿/已外发可作废）")
    @Parameter(name = "id", description = "培训记录编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-training:update')")
    public CommonResult<Boolean> voidTraining(@RequestParam("id") Long id) {
        trainingService.voidTraining(id);
        return success(true);
    }

    @PostMapping("/generate-file")
    @Operation(summary = "生成培训记录表文件（下载用，幂等重生成）")
    @Parameter(name = "id", description = "培训记录编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public CommonResult<String> generateRecordFile(@RequestParam("id") Long id) {
        return success(trainingService.generateRecordFile(id));
    }
}
