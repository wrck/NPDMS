package cn.iocoder.yudao.module.pms.platform.controller.admin.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.user.BusinessUserSelectionApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Ordinary business dropdown candidates; business mutations retain their own authorization. */
@RestController
@RequestMapping("/api/v1/pms/user-selections")
@Validated
@RequiredArgsConstructor
public class BusinessUserSelectionController {
    private final BusinessUserSelectionApi selections;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<BusinessUserSelectionApi.Option>> page(@Valid PageRequest request) {
        return success(selections.page(new BusinessUserSelectionApi.Query(
                request.getPageNo(), request.getPageSize(), request.getKeyword(), request.getUserIds())));
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PageRequest extends PageParam {
        @Size(max = 64)
        private String keyword;

        @Size(max = 100)
        private Set<@NotNull @Positive Long> userIds;

        @Override
        @Max(100)
        public Integer getPageSize() {
            return super.getPageSize();
        }
    }
}
