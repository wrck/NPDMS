package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.CutoverWorkbenchTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverWorkbenchTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.query.CutoverWorkbenchTaskListQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/cutover-workbench/tasks")
@RequiredArgsConstructor
public class CutoverWorkbenchTaskController {
    private final CutoverTaskRequestContext requestContext;
    private final CutoverWorkbenchTaskQueryService service;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:query')")
    public CommonResult<PageResult<CutoverWorkbenchTaskQueryService.Item>> page(
            @Valid @ModelAttribute CutoverWorkbenchTaskPageReqVO request) {
        return success(service.page(requestContext.current(), CutoverWorkbenchTaskListQuery.builder()
                .listKind(CutoverWorkbenchTaskListQuery.ListKind.valueOf(request.getListKind()))
                .projectId(request.getProjectId()).currentStage(request.getCurrentStage()).taskStatus(request.getTaskStatus())
                .pageNo(request.getPageNo()).pageSize(request.getPageSize()).build()));
    }

    @ExceptionHandler(CutoverTaskApplicationException.class)
    public ResponseEntity<CommonResult<CutoverTaskErrorData>> handleApplication(CutoverTaskApplicationException exception) {
        return CutoverRuntimeTaskController.applicationError(exception);
    }
}
