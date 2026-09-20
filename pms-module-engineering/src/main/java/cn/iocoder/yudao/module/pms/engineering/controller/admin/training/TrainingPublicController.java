package cn.iocoder.yudao.module.pms.engineering.controller.admin.training;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicConfirmReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.training.vo.TrainingPublicRespVO;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 公开端 - 现场培训记录客户确认 Controller（ACC-01，Demo 6.1 移动端）。
 * <p>
 * 与满意度公开问卷相同的 withTenant 模式：令牌仅存摘要，原始令牌经外发链接携带；
 * 前端公开路由 {@code /training-records/:token}。
 */
@Tag(name = "公开端 - 现场培训记录客户确认")
@RestController
@RequestMapping("/api/v1/pms/training-records")
@RequiredArgsConstructor
public class TrainingPublicController {

    private final TrainingService trainingService;
    private final Environment environment;

    @GetMapping("/{token}")
    @PermitAll
    @Operation(summary = "按令牌查看培训记录")
    public CommonResult<TrainingPublicRespVO> inspect(@PathVariable("token") String token) {
        return withTenant(() -> success(trainingService.inspectByToken(token)));
    }

    @PostMapping("/{token}/confirm")
    @PermitAll
    @Operation(summary = "客户签字确认培训记录（确认后自动归档交付件）")
    public CommonResult<Boolean> confirm(@PathVariable("token") String token,
                                         @Valid @RequestBody TrainingPublicConfirmReqVO reqVO) {
        withTenant(() -> {
            trainingService.confirmByToken(token, reqVO);
            return true;
        });
        return success(true);
    }

    private <T> T withTenant(Supplier<T> action) {
        if (TenantContextHolder.getTenantId() != null) return action.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(action.get()));
        return result.get();
    }
}
