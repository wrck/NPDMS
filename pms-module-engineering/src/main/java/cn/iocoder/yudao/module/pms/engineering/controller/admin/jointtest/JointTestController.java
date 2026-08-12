package cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.service.jointtest.JointTestService;
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
 * 管理后台 - PMS 业务联调 Controller（FR-ENG-024）。
 * <p>
 * 路径前缀 {@code /pms/eng-joint-test}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-joint-test:*}。
 */
@Tag(name = "管理后台 - PMS 业务联调")
@RestController
@RequestMapping("/pms/eng-joint-test")
@Validated
public class JointTestController {

    @Resource
    private JointTestService jointTestService;

    @PostMapping("/create")
    @Operation(summary = "创建联调记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:create')")
    public CommonResult<Long> createJointTest(@Valid @RequestBody JointTestSaveReqVO createReqVO) {
        return success(jointTestService.createJointTest(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新联调记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:update')")
    public CommonResult<Boolean> updateJointTest(@Valid @RequestBody JointTestSaveReqVO updateReqVO) {
        jointTestService.updateJointTest(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除联调记录")
    @Parameter(name = "id", description = "联调编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:delete')")
    public CommonResult<Boolean> deleteJointTest(@RequestParam("id") Long id) {
        jointTestService.deleteJointTest(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询联调详情")
    @Parameter(name = "id", description = "联调编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:query')")
    public CommonResult<JointTestRespVO> getJointTest(@RequestParam("id") Long id) {
        JointTestDO entity = jointTestService.getJointTest(id);
        return success(BeanUtils.toBean(entity, JointTestRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询联调记录")
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:query')")
    public CommonResult<PageResult<JointTestRespVO>> getJointTestPage(@Validated JointTestPageReqVO pageReqVO) {
        PageResult<JointTestDO> pageResult = jointTestService.getJointTestPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, JointTestRespVO.class));
    }

    @PutMapping("/start")
    @Operation(summary = "开始联调（0待联调 → 1进行中）")
    @Parameter(name = "id", description = "联调编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:update')")
    public CommonResult<Boolean> start(@RequestParam("id") Long id) {
        jointTestService.start(id);
        return success(true);
    }

    @PutMapping("/pass")
    @Operation(summary = "联调通过（1进行中 → 2通过）")
    @Parameter(name = "id", description = "联调编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:update')")
    public CommonResult<Boolean> pass(@RequestParam("id") Long id) {
        jointTestService.pass(id);
        return success(true);
    }

    @PutMapping("/fail")
    @Operation(summary = "联调失败（1进行中 → 3失败，必须记录异常）")
    @PreAuthorize("@ss.hasPermission('pms:eng-joint-test:update')")
    public CommonResult<Boolean> fail(@RequestParam("id") Long id,
                                      @RequestParam("exceptionRecord") String exceptionRecord) {
        jointTestService.fail(id, exceptionRecord);
        return success(true);
    }
}
