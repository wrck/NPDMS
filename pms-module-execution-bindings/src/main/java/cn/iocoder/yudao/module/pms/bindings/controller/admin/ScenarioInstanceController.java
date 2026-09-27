package cn.iocoder.yudao.module.pms.bindings.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.bindings.service.ScenarioExecutionService;
import cn.iocoder.yudao.module.pms.bindings.service.ScenarioRunOutcome;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.result.BusinessResultRecord;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 场景执行入口：同步运行、实例与结果查看；权限复用过程定义的查询/操作权限，
 * 入口本身不承载任何实体或后端专属分支。
 */
@RestController
@RequestMapping("/api/v1/pms/scenario-instances")
@Tag(name = "管理后台 - PMS 场景执行实例")
@Validated
@RequiredArgsConstructor
public class ScenarioInstanceController {

    private final ScenarioExecutionService executionService;

    @Data
    public static class ScenarioRunReqVO {

        @NotBlank
        private String definitionCode;

        @NotNull
        private Long entityId;

        @NotBlank
        private String idempotencyKey;

        /** 可选；缺省为同步直执后端。 */
        private String backendId;
    }

    @PostMapping("/run")
    @PreAuthorize("@ss.hasPermission('pms:process-definition:operate')")
    public CommonResult<ScenarioRunOutcome> run(@Valid @RequestBody ScenarioRunReqVO reqVO) {
        return success(executionService.run(reqVO.getDefinitionCode(), reqVO.getEntityId(),
                reqVO.getIdempotencyKey(), reqVO.getBackendId()));
    }

    @GetMapping("/instances")
    @PreAuthorize("@ss.hasPermission('pms:process-definition:query')")
    public CommonResult<List<ScenarioRunOutcome>> instances(@RequestParam String definitionCode) {
        return success(executionService.listInstances(definitionCode));
    }

    @GetMapping("/results")
    @PreAuthorize("@ss.hasPermission('pms:process-definition:query')")
    public CommonResult<List<BusinessResultRecord>> results(@RequestParam String definitionCode) {
        return success(executionService.listResults(definitionCode));
    }
}
