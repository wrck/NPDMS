package cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.service.configuration.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 配置调试 Controller（FR-ENG-023）。
 * <p>
 * 路径前缀 {@code /pms/eng-configuration}。
 */
@Tag(name = "管理后台 - 配置调试")
@RestController
@RequestMapping("/pms/eng-configuration")
@Validated
public class ConfigurationController {

    @Resource
    private ConfigurationService configurationService;

    @PostMapping("/create")
    @Operation(summary = "创建配置调试记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:create')")
    public CommonResult<Long> createConfiguration(@Valid @RequestBody ConfigurationSaveReqVO createReqVO) {
        return success(configurationService.createConfiguration(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新配置调试记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:update')")
    public CommonResult<Boolean> updateConfiguration(@Valid @RequestBody ConfigurationSaveReqVO updateReqVO) {
        configurationService.updateConfiguration(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除配置调试记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:delete')")
    public CommonResult<Boolean> deleteConfiguration(@RequestParam("id") Long id) {
        configurationService.deleteConfiguration(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询配置调试详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:query')")
    public CommonResult<ConfigurationRespVO> getConfiguration(@RequestParam("id") Long id) {
        ConfigurationDO configuration = configurationService.getConfiguration(id);
        return success(BeanUtils.toBean(configuration, ConfigurationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询配置调试")
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:query')")
    public CommonResult<PageResult<ConfigurationRespVO>> getConfigurationPage(@Validated ConfigurationPageReqVO pageReqVO) {
        PageResult<ConfigurationDO> pageResult = configurationService.getConfigurationPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ConfigurationRespVO.class));
    }

    @PutMapping("/start")
    @Operation(summary = "开始调试")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:update')")
    public CommonResult<Boolean> startConfiguration(@RequestParam("id") Long id) {
        configurationService.startConfiguration(id);
        return success(true);
    }

    @PutMapping("/complete")
    @Operation(summary = "完成调试")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:update')")
    public CommonResult<Boolean> completeConfiguration(@RequestParam("id") Long id) {
        configurationService.completeConfiguration(id);
        return success(true);
    }

    @PutMapping("/mark-abnormal")
    @Operation(summary = "标记配置异常")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-configuration:update')")
    public CommonResult<Boolean> markAbnormal(@RequestParam("id") Long id) {
        configurationService.markAbnormal(id);
        return success(true);
    }
}
