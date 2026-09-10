package cn.iocoder.yudao.module.pms.project.controller.admin.projectmember;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMemberAssignmentRespVO;
import cn.iocoder.yudao.module.pms.project.service.projectmember.OrdinaryProjectMemberService;
import cn.iocoder.yudao.module.pms.project.service.projectmember.OrdinaryProjectMemberService.*;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;

@Tag(name = "管理后台 - 项目统一成员")
@RestController
@RequestMapping("/api/v1/pms/projects")
@Validated
@RequiredArgsConstructor
public class OrdinaryProjectMemberController {
    private final OrdinaryProjectMemberService members;
    private final cn.iocoder.yudao.module.system.api.user.AdminUserApi users;

    @GetMapping("/{id}/members")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PageResult<ProjectMemberAssignmentRespVO>> page(@PathVariable("id") @Positive Long id,
            @Valid MemberPageRequest request) {
        var page = BeanUtils.toBean(members.page(id, new Filter(request.getPageNo(), request.getPageSize(),
                request.getState(), request.getRole(), request.getKeyword()), actor()), ProjectMemberAssignmentRespVO.class);
        // 先由项目服务完成范围授权，只读取本页成员的联系方式，不读取任意人员列表。
        if (!page.getList().isEmpty()) {
            var people = users.getUserMap(page.getList().stream().map(ProjectMemberAssignmentRespVO::getUserId)
                    .filter(java.util.Objects::nonNull).distinct().toList());
            page.getList().forEach(member -> {
                var person = people.get(member.getUserId());
                if (person != null) {
                    member.setMobile(person.getMobile());
                    member.setEmail(person.getEmail());
                }
            });
        }
        return success(page);
    }

    @GetMapping("/{id}/member-candidates")
    @PreAuthorize("@ss.hasAnyPermissions('pms:project-team:create', 'pms:project:assign')")
    public CommonResult<PageResult<MemberCandidate>> candidates(@PathVariable("id") @Positive Long id,
            @Valid CandidateRequest request) {
        var page = members.candidates(id, new CandidateFilter(request.getPageNo(), request.getPageSize(),
                request.getKeyword(), request.getUserId(), request.getProjectRole(), request.scope()), actor());
        if (page.getList().isEmpty()) return success(PageResult.empty());
        var contacts = users.getUserMap(page.getList().stream().map(ActiveUserSelectionApi.User::id).toList());
        return success(new PageResult<>(page.getList().stream().map(person -> {
            var contact = contacts.get(person.id());
            return new MemberCandidate(person.id(), person.username(), person.nickname(),
                    contact == null ? null : contact.getMobile(), contact == null ? null : contact.getEmail());
        }).toList(), page.getTotal()));
    }

    public record MemberCandidate(Long id, String username, String nickname, String mobile, String email) { }

    @PostMapping("/{id}/members")
    @PreAuthorize("@ss.hasAnyPermissions('pms:project-team:create', 'pms:project:assign')")
    public CommonResult<Result> add(@PathVariable("id") @Positive Long id,
            @RequestHeader("If-Match") String version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody MemberRequest request) {
        return success(members.mutate(command(id, null, Action.ADD, request, version, key), actor()));
    }

    @PutMapping("/{id}/members/{assignmentId}")
    @PreAuthorize("@ss.hasAnyPermissions('pms:project-team:create', 'pms:project:assign')")
    public CommonResult<Result> update(@PathVariable("id") @Positive Long id,
            @PathVariable("assignmentId") @Positive Long assignmentId,
            @RequestHeader("If-Match") String version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody MemberRequest request) {
        return success(members.mutate(command(id, assignmentId, Action.UPDATE, request, version, key), actor()));
    }

    @PostMapping("/{id}/members/{assignmentId}/actions/remove")
    @PreAuthorize("@ss.hasAnyPermissions('pms:project-team:create', 'pms:project:assign')")
    public CommonResult<Result> remove(@PathVariable("id") @Positive Long id,
            @PathVariable("assignmentId") @Positive Long assignmentId,
            @RequestHeader("If-Match") String version,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
            @Valid @RequestBody ReasonRequest request) {
        return success(members.mutate(new Command(id, version(version), Action.REMOVE, assignmentId,
                null, request.reason(), key, request.replacementPrimaryUserId()), actor()));
    }

    private Command command(Long id, Long assignmentId, Action action, MemberRequest request, String version, String key) {
        return new Command(id, version(version), action, assignmentId, new MemberValues(request.userId(),
                request.memberRole(), request.responsibility(), request.remark(), request.primary(), request.scope()), request.reason(), key);
    }
    private static Actor actor() {
        return new Actor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                UUID.randomUUID().toString());
    }
    private static int version(String header) {
        String value = header.trim();
        if (value.startsWith("W/")) value = value.substring(2).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        try { int version = Integer.parseInt(value); if (version >= 0) return version; }
        catch (NumberFormatException ignored) { }
        throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "If-Match版本无效");
    }
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MemberPageRequest extends PageParam {
        @NotBlank @Pattern(regexp = "CURRENT|HISTORY") private String state = "CURRENT";
        @Size(max = 32) private String role;
        @Size(max = 64) private String keyword;
    }
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CandidateRequest extends PageParam {
        @NotBlank @Pattern(regexp = "PROJECT_MANAGER|SERVICE_MANAGER|TEAM_MEMBER|SALES_REPRESENTATIVE") private String projectRole;
        @Size(max = 64) private String keyword;
        @Positive private Long userId;
        @Size(max = 2) private String levelCode;
        @Size(max = 32) private String assignmentType;
        @Positive private Long siteId;
        @Positive private Long departmentId;
        @Size(max = 64) private String departmentCode;
        ServiceScope scope() {
            return "SERVICE_MANAGER".equals(projectRole)
                    ? new ServiceScope(levelCode, assignmentType, siteId, departmentId, departmentCode) : null;
        }
    }
    public record MemberRequest(@NotNull @Positive Long userId, @NotBlank @Size(max = 32) String memberRole,
            @Size(max = 500) String responsibility, @Size(max = 500) String remark,
            @Size(max = 500) String reason, Boolean primary, ServiceScope scope) { }
    public record ReasonRequest(@NotBlank @Size(max = 500) String reason, @Positive Long replacementPrimaryUserId) { }
}
