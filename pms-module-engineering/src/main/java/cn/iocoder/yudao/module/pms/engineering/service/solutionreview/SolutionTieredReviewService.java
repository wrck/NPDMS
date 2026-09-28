package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.solutionreview.*;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.solution.vo.SolutionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solutionreview.SolutionReviewDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solutionreview.SolutionReviewMapper.SolutionReviewQuery;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionService;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.project.api.participant.*;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** Independent SOL commands; the existing ordinary-review service and its routes remain intact. */
@Service @RequiredArgsConstructor
public class SolutionTieredReviewService {
    private final SolutionMapper solutions;
    private final SolutionReviewMapper reviews;
    private final SolutionReviewBpmApi bpm;
    private final ProjectScopeApi scope;
    private final ProjectParticipantFactApi participants;
    private final PermissionApi permissions;
    private final EngineeringRuleReevaluationEvents events;
    private final PlatformDeliveryMaterialApi deliveryMaterials;
    private final SolutionService original;
    private final SolutionReviewPolicyService policies;

    public record Start(@jakarta.validation.constraints.NotNull Long projectId,
                        @jakarta.validation.constraints.NotNull Long solutionId,
                        @jakarta.validation.constraints.NotNull Integer expectedVersion,
                        @jakarta.validation.constraints.NotBlank String processDefinitionId,
                        @jakarta.validation.constraints.NotEmpty Map<String, @jakarta.validation.constraints.NotNull Long> candidates) { }
    public record Selection(@jakarta.validation.constraints.NotNull Long projectId,
                            @jakarta.validation.constraints.NotNull Long solutionId) { }

    public SolutionDO sourceForReview(String businessKey) {
        if (businessKey == null || !businessKey.matches("SOL_REVIEW:[1-9][0-9]*:[1-9][0-9]*")) throw exception(FORBIDDEN);
        var parts = businessKey.split(":");
        if (!Objects.equals(parts[1], TenantContextHolder.getRequiredTenantId().toString())) throw exception(FORBIDDEN);
        var solution = solutions.selectById(Long.valueOf(parts[2]));
        if (solution == null) throw exception(FORBIDDEN);
        authorize(solution.getProjectId(), false);
        var review = reviews.bySolution(new SolutionReviewQuery(TenantContextHolder.getRequiredTenantId(), solution.getProjectId(), solution.getId(), false));
        if (review == null || !Objects.equals(review.getBusinessKey(), businessKey)) throw exception(FORBIDDEN);
        return solution;
    }

    public SolutionReviewDO read(Selection selection) {
        authorize(selection.projectId(), false);
        load(selection, false);
        return reviews.bySolution(query(selection, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public SolutionReviewDO start(Start command) {
        Long actor = authorize(command.projectId(), true);
        var selection = new Selection(command.projectId(), command.solutionId());
        var solution = load(selection, true);
        var existing = reviews.bySolution(query(selection, true));
        if (existing != null) {
            if (Objects.equals(existing.getSubmittedBy(), actor) && "RUNNING".equals(existing.getStatus())
                    && Objects.equals(existing.getRequestVersion(), command.expectedVersion() == null ? null : command.expectedVersion().longValue())
                    && Objects.equals(existing.getProcessDefinitionId(), command.processDefinitionId())
                    && Objects.equals(JsonUtils.parseTree(existing.getCandidatesJson()), JsonUtils.parseTree(JsonUtils.toJsonString(command.candidates())))) return existing;
            throw new IllegalArgumentException("此方案已提交分级审批；请查看结果或从终态创建新版本");
        }
        Long expectedVersion = command.expectedVersion() == null ? null : command.expectedVersion().longValue();
        if (!Objects.equals(solution.getVersion(), expectedVersion) || !Set.of(0, 1, 2).contains(solution.getStatus()))
            throw new IllegalArgumentException("只能提交当前未批准的重大方案版本");
        Integer previousLevel = solution.getReviewLevel();
        policies.freeze(solution, 1);
        if (!Integer.valueOf(1).equals(solution.getReviewLevel())) throw new IllegalArgumentException("本方案不适用分级审核");
        if (solution.getStatus() == 2 && !Objects.equals(previousLevel, solution.getReviewLevel())) update(solution);
        // Move through the same submitted/reviewing lifecycle; never reset a terminal solution to a draft.
        if (solution.getStatus() == 0) {
            cn.iocoder.yudao.module.pms.engineering.domain.SolutionStatusRules.requireTransition(solution.getStatus(),
                    cn.iocoder.yudao.module.pms.engineering.domain.SolutionStatusRules.Action.SUBMIT);
            solution.setStatus(1); update(solution);
        }
        if (solution.getStatus() == 1) {
            cn.iocoder.yudao.module.pms.engineering.domain.SolutionStatusRules.requireTransition(solution.getStatus(),
                    cn.iocoder.yudao.module.pms.engineering.domain.SolutionStatusRules.Action.START_REVIEW);
            solution.setStatus(2); update(solution);
        }
        String businessKey = "SOL_REVIEW:" + TenantContextHolder.getRequiredTenantId() + ":" + solution.getId();
        var review = new SolutionReviewDO();
        review.setProjectId(solution.getProjectId()); review.setSolutionId(solution.getId());
        review.setSourceVersion(solution.getVersion()); review.setBusinessKey(businessKey);
        review.setRequestVersion(command.expectedVersion() == null ? null : command.expectedVersion().longValue());
        review.setProcessDefinitionId(command.processDefinitionId());
        review.setCandidatesJson(JsonUtils.toJsonString(command.candidates()));
        review.setStatus("RUNNING"); review.setVersion(0); review.setSubmittedBy(actor); review.setSubmittedAt(LocalDateTime.now());
        reviews.insert(review);
        var started = bpm.start(new SolutionReviewBpmApi.Start(TenantContextHolder.getRequiredTenantId(), actor,
                solution.getProjectId(), businessKey, command.processDefinitionId(), command.candidates()));
        review.setProcessInstanceId(started.instanceId());
        if (!Objects.equals(started.definitionId(), command.processDefinitionId()) || reviews.updateById(review) != 1)
            throw new IllegalStateException("审批定义或方案审批版本已变化");
        changed(solution, actor);
        return review;
    }

    @Transactional(rollbackFor = Exception.class)
    public SolutionReviewDO refresh(Selection selection) {
        authorize(selection.projectId(), true);
        load(selection, true);
        var review = reviews.bySolution(query(selection, true));
        if (review == null) throw new IllegalArgumentException("方案尚未发起分级审批");
        apply(review);
        return reviews.selectById(review.getId());
    }

    public SolutionReviewPolicyService.Policy policy(Long projectId) {
        authorize(projectId, false);
        return policies.preview(projectId);
    }

    @EventListener @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void onResult(SolutionReviewResultEvent event) {
        if (!Objects.equals(event.tenantId(), TenantContextHolder.getTenantId())) throw exception(FORBIDDEN);
        var located = reviews.byInstance(event.tenantId(), event.instanceId());
        if (located == null) throw new IllegalStateException("审批实例缺少 SOL 版本关联");
        load(new Selection(located.getProjectId(), located.getSolutionId()), true);
        apply(reviews.bySolution(new SolutionReviewQuery(event.tenantId(), located.getProjectId(), located.getSolutionId(), true)));
    }

    private void apply(SolutionReviewDO review) {
        if (!"RUNNING".equals(review.getStatus())) return;
        var result = bpm.result(TenantContextHolder.getRequiredTenantId(), review.getProcessInstanceId());
        if (!Objects.equals(result.definitionId(), review.getProcessDefinitionId())
                || !Objects.equals(result.businessKey(), review.getBusinessKey())
                || !Objects.equals(result.instanceId(), review.getProcessInstanceId()))
            throw new IllegalStateException("方案审批结果身份不匹配");
        if (!Set.of("APPROVE", "REJECT", "CANCEL").contains(result.status())) return;
        var solution = load(new Selection(review.getProjectId(), review.getSolutionId()), true);
        if (!Integer.valueOf(2).equals(solution.getStatus()) || !Objects.equals(solution.getVersion(), review.getSourceVersion()))
            throw new IllegalStateException("审批期间方案已变化，不得覆盖当前事实");
        boolean approved = "APPROVE".equals(result.status());
        solution.setStatus(approved ? 3 : "REJECT".equals(result.status()) ? 4 : 5);
        var last = result.reviews().stream().filter(item -> item.userId() != null && item.time() != null)
                .max(Comparator.comparing(SolutionReviewBpmApi.Review::time)).orElse(null);
        if (approved && last == null) throw new IllegalStateException("缺少最终人工审核历史");
        solution.setApprovedBy(last == null ? null : last.userId());
        solution.setApprovedTime(last == null ? null : last.time());
        solution.setApprovalOpinion(last == null ? "审批已撤回" : last.reason());
        if (approved) solution.setBaselineVersion(Math.toIntExact(solution.getVersion() + 1));
        update(solution);
        review.setStatus(result.status()); review.setReviewsJson(JsonUtils.toJsonString(result.reviews()));
        review.setCompletedAt(LocalDateTime.now());
        if (approved) { review.setApprovedVersion(solution.getVersion()); archive(solution); }
        if (reviews.updateById(review) != 1) throw new IllegalStateException("方案审批版本冲突");
        changed(solution, review.getSubmittedBy());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long revise(Selection selection) {
        authorize(selection.projectId(), true);
        var solution = load(selection, true);
        var review = reviews.bySolution(query(selection, true));
        if (review == null || !Set.of("APPROVE", "REJECT", "CANCEL").contains(review.getStatus()))
            throw new IllegalArgumentException("请先结束当前审批再创建新的方案版本");
        var draft = BeanUtils.toBean(solution, SolutionSaveReqVO.class);
        draft.setId(null); draft.setVersion(0);
        draft.setRemark("源方案 " + solution.getCode() + "；审批实例 " + review.getProcessInstanceId());
        return original.createSolution(draft);
    }

    /** Used after the caller locks the source solution; no query writes or historical snapshot replacement. */
    public boolean approved(SolutionDO solution, boolean lock) {
        var review = reviews.bySolution(new SolutionReviewQuery(solution.getTenantId(), solution.getProjectId(), solution.getId(), lock));
        if (review == null || !"APPROVE".equals(review.getStatus())
                || !Objects.equals(review.getApprovedVersion(), solution.getVersion()) || review.getCompletedAt() == null) return false;
        var result = bpm.result(solution.getTenantId(), review.getProcessInstanceId());
        return "APPROVE".equals(result.status()) && Objects.equals(result.definitionId(), review.getProcessDefinitionId())
                && Objects.equals(result.businessKey(), review.getBusinessKey());
    }

    private Long authorize(Long project, boolean write) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || !permissions.hasAnyPermissions(actor, write ? "pms:sol-solution:update" : "pms:sol-solution:query")) throw exception(FORBIDDEN);
        var visible = scope.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, project,
                write ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW));
        if (visible == null || !visible.fullProjectIds().contains(project)) throw exception(FORBIDDEN);
        if (write) {
            var fact = participants.inspect(new ProjectParticipantFactQuery(project, actor, Set.of(ProjectMemberRoles.PROJECT_MANAGER), LocalDateTime.now()));
            if (fact == null || !fact.effectiveRoleCodes().contains(ProjectMemberRoles.PROJECT_MANAGER)) throw exception(FORBIDDEN);
            participants.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(project, actor, fact.projectVersion(), "ACTIVE", null,
                    Set.of(ProjectMemberRoles.PROJECT_MANAGER)));
        }
        return actor;
    }
    private SolutionReviewQuery query(Selection selection, boolean lock) {
        return new SolutionReviewQuery(TenantContextHolder.getRequiredTenantId(), selection.projectId(), selection.solutionId(), lock);
    }
    private SolutionDO load(Selection selection, boolean lock) {
        var solution = reviews.source(query(selection, lock));
        if (solution == null) throw new IllegalArgumentException("本项目实施方案不存在");
        return solution;
    }
    private void update(SolutionDO solution) {
        if (solutions.updateById(solution) != 1) throw new IllegalStateException("方案版本冲突");
    }
    private void changed(SolutionDO solution, Long actor) {
        events.changed(solution.getProjectId(), "ImplementationSolution", solution.getId(), actor,
                "solution-review:" + solution.getId() + ":" + solution.getVersion());
    }
    /** 复审通过归档实施方案交付件：统一登记为业务结果型交付件（P06R），同一方案幂等。 */
    private void archive(SolutionDO solution) {
        deliveryMaterials.registerBusinessResultMaterial("SOL", "solution", solution.getId(),
                "IMPLEMENTATION_PLAN", "solution", String.valueOf(solution.getId()),
                solution.getBaselineVersion() == null ? null : solution.getBaselineVersion().longValue(),
                solution.getName() + "（基线v" + solution.getBaselineVersion() + "）",
                solution.getProjectId());
    }
}
