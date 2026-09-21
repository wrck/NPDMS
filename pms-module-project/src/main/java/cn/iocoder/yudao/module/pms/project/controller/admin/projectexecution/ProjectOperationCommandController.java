package cn.iocoder.yudao.module.pms.project.controller.admin.projectexecution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** The request cannot downgrade itself to independent mode. Owner and project authorizations run server-side. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/project-execution/operations")
public class ProjectOperationCommandController {
    private final ProjectOperationDispatcher operations;
    @PostMapping("/{operationCode}")
    public CommonResult<ProjectOperationResult> execute(@PathVariable String operationCode,
            @RequestHeader("Idempotency-Key") String key, @RequestBody ProjectOperationCommand body) {
        if (body == null) throw exception(BAD_REQUEST, "OPERATION_COMMAND_INVALID");
        var command = body.withKey(key);
        return success(operations.execute(operationCode, command));
    }
}
