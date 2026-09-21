package cn.iocoder.yudao.module.pms.engineering.service.stageplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueSummaryVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanBatchMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanItemMapper;
import cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 阶段施工计划批次 Service 实现（PLN-01/04，Demo 3.1）。
 * <p>
 * 推算：读取冻结项目路径和占比；直签取计划验收时间，非直签取生效工期窗口。
 * 阶段与任务日期、验收约束和输入快照随审批版本保留。
 * 审批：提交时发起 BPM 流程；通过后经受控写入契约（applyPlanDates）回写阶段计划日期。
 */
@Service
@Validated
@Slf4j
public class StagePlanBatchServiceImpl implements StagePlanBatchService {

    private static final String APPROVAL_TASK = "serviceManagerApprove";

    @Resource
    private StagePlanBatchMapper batchMapper;
    @Resource
    private StagePlanItemMapper itemMapper;
    @Resource
    private ProjectStagePlanApi stagePlanApi;
    @Resource
    private ConstructionPlanMapper constructionPlanMapper;
    @Resource
    private ConstructionPlanRevisionMapper constructionPlanRevisionMapper;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private StagePlanProperties properties;
    @Resource
    private Environment environment;
    @Resource
    private cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScopeApi;
    @Resource
    private cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi participants;

    private java.util.Set<Long> visibleProjects(String action) {
        return projectScopeApi.resolveAllCurrent(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery(
                TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), action));
    }
    private void requireAccess(Long projectId, boolean write) {
        if (!visibleProjects(write ? "PROJECT_EDIT" : "PROJECT_VIEW").contains(projectId))
            throw exception(cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StagePlanBatchRespVO createDraft(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw exception(STAGE_PLAN_ARGUMENT_INVALID, "项目编号无效");
        }
        requireAccess(projectId, true);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var durationRoot = constructionPlanMapper.selectByProjectId(tenantId, projectId);
        if (durationRoot == null || constructionPlanMapper.selectForUpdate(
                new cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery(tenantId, durationRoot.getId())) == null)
            throw exception(STAGE_PLAN_DATE_INVALID, "请先录入生效工期");
        batchMapper.selectListByProject(projectId).stream()
                .filter(batch -> batch.getStatus() != null
                        && (batch.getStatus() == StagePlanBatchDO.STATUS_DRAFT
                        || batch.getStatus() == StagePlanBatchDO.STATUS_PENDING_APPROVAL))
                .findFirst()
                .ifPresent(batch -> {
                    throw exception(STAGE_PLAN_ACTIVE_BATCH_EXISTS);
                });
        List<ProjectStagePlanApi.StagePlanFact> stages =
                stagePlanApi.listStages(tenantId, projectId);
        if (stages.isEmpty()) {
            throw exception(STAGE_PLAN_NO_STAGES);
        }
        ConstructionPlanRevisionDO baseline = loadBaseline(tenantId, projectId);
        if (baseline == null) throw exception(STAGE_PLAN_DATE_INVALID, "请先录入生效工期");
        ProjectStagePlanApi.ScheduleCalculation calculation;
        try { calculation = stagePlanApi.calculateSchedule(tenantId, projectId, baseline.getStartDate(), baseline.getEndDate()); }
        catch (IllegalArgumentException failure) { throw exception(STAGE_PLAN_DATE_INVALID, failure.getMessage()); }
        if (calculation == null) throw exception(STAGE_PLAN_DATE_INVALID, "项目计划推算输入不可用");
        var calculated = calculation.stages().stream().collect(java.util.stream.Collectors.toMap(ProjectStagePlanApi.StagePlanDate::stageId, value -> value));
        var factsById = stages.stream().collect(java.util.stream.Collectors.toMap(ProjectStagePlanApi.StagePlanFact::stageId, stage -> stage));
        stages = calculation.stages().stream().map(date -> factsById.get(date.stageId())).toList();
        if (stages.stream().anyMatch(Objects::isNull)) throw exception(STAGE_PLAN_DATE_INVALID, "冻结路径阶段实例缺失");
        StagePlanBatchDO batch = new StagePlanBatchDO();
        batch.setProjectId(projectId);
        batch.setStatus(StagePlanBatchDO.STATUS_DRAFT);
        batch.setDurationRevisionId(baseline.getId());
        batch.setCalculatedStart(calculation.start()); batch.setCalculatedEnd(calculation.end());
        batch.setInputSnapshot(calculation.inputSnapshot());
        var participatingCodes = stages.stream().map(ProjectStagePlanApi.StagePlanFact::code).collect(java.util.stream.Collectors.toSet());
        batch.setTaskPlansJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(stagePlanApi.listTaskPlans(tenantId, projectId).stream()
                .filter(task -> participatingCodes.contains(task.stageCode())).toList()));
        batchMapper.insert(batch);
        int pathOrder = 0;
        for (ProjectStagePlanApi.StagePlanFact stage : stages) {
            StagePlanItemDO item = new StagePlanItemDO();
            item.setBatchId(batch.getId());
            item.setPhaseId(stage.stageId());
            item.setPhaseCode(stage.code());
            item.setPhaseName(stage.name());
            item.setSort(pathOrder++);
            var dates = calculated.get(stage.stageId());
            item.setSuggestedStart(dates.planStartTime());
            item.setSuggestedEnd(dates.planEndTime());
            item.setPlanStart(dates.planStartTime()); item.setPlanEnd(dates.planEndTime());
            itemMapper.insert(item);
        }
        return getBatch(batch.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StagePlanBatchRespVO autoEstimate(Long batchId) {
        StagePlanBatchDO batch = validateBatchEditable(batchId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<StagePlanItemDO> items = itemMapper.selectListByBatchId(batchId);
        ConstructionPlanRevisionDO baseline = loadBaseline(tenantId, batch.getProjectId());
        if (baseline == null) throw exception(STAGE_PLAN_DATE_INVALID, "请先录入生效工期");
        ProjectStagePlanApi.ScheduleCalculation calculation;
        try {
            calculation = stagePlanApi.calculateSchedule(tenantId, batch.getProjectId(), baseline.getStartDate(), baseline.getEndDate());
        } catch (IllegalArgumentException failure) {
            throw exception(STAGE_PLAN_DATE_INVALID, failure.getMessage());
        }
        if (calculation == null) throw exception(STAGE_PLAN_DATE_INVALID, "项目计划推算输入不可用");
        var byStage = calculation.stages().stream().collect(java.util.stream.Collectors.toMap(ProjectStagePlanApi.StagePlanDate::stageId, value -> value));
        if (!byStage.keySet().equals(items.stream().map(StagePlanItemDO::getPhaseId).collect(java.util.stream.Collectors.toSet()))) {
            var facts = stagePlanApi.listStages(tenantId, batch.getProjectId()).stream()
                    .collect(java.util.stream.Collectors.toMap(ProjectStagePlanApi.StagePlanFact::stageId, value -> value));
            var replacements = new ArrayList<StagePlanItemDO>();
            for (var date : calculation.stages()) {
                var fact = facts.get(date.stageId());
                if (fact == null) throw exception(STAGE_PLAN_DATE_INVALID, "冻结路径阶段实例缺失");
                var item = new StagePlanItemDO();
                item.setBatchId(batchId); item.setPhaseId(fact.stageId()); item.setPhaseCode(fact.code()); item.setPhaseName(fact.name());
                item.setPlanStart(date.planStartTime()); item.setPlanEnd(date.planEndTime()); item.setSort(replacements.size());
                item.setSuggestedStart(date.planStartTime()); item.setSuggestedEnd(date.planEndTime());
                replacements.add(item);
            }
            // Only an editable draft is rebuilt. Approved batches and their snapshots are never changed.
            itemMapper.deleteByBatchId(batchId);
            replacements.forEach(itemMapper::insert);
            items = replacements;
        }
        var pathOrder = new java.util.HashMap<Long, Integer>();
        for (int i = 0; i < calculation.stages().size(); i++) pathOrder.put(calculation.stages().get(i).stageId(), i);
        for (var item : items) {
            var dates = byStage.get(item.getPhaseId()); item.setPlanStart(dates.planStartTime()); item.setPlanEnd(dates.planEndTime());
            item.setSuggestedStart(dates.planStartTime()); item.setSuggestedEnd(dates.planEndTime());
            item.setSort(pathOrder.get(item.getPhaseId()));
        }
        batch.setDurationRevisionId(baseline.getId());
        for (StagePlanItemDO item : items) {
            StagePlanItemDO update = new StagePlanItemDO();
            update.setId(item.getId());
            update.setPlanStart(item.getPlanStart());
            update.setPlanEnd(item.getPlanEnd());
            update.setSuggestedStart(item.getSuggestedStart());
            update.setSuggestedEnd(item.getSuggestedEnd());
            update.setSort(item.getSort());
            itemMapper.updateById(update);
        }
        StagePlanBatchDO batchUpdate = new StagePlanBatchDO();
        batchUpdate.setId(batch.getId());
        batchUpdate.setDurationRevisionId(batch.getDurationRevisionId());
        batchUpdate.setCalculatedStart(calculation.start()); batchUpdate.setCalculatedEnd(calculation.end());
        batchUpdate.setInputSnapshot(calculation.inputSnapshot());
        var participating = items.stream().map(StagePlanItemDO::getPhaseCode).collect(java.util.stream.Collectors.toSet());
        var previousTasks = readTasks(batch).stream().collect(java.util.stream.Collectors.toMap(ProjectStagePlanApi.TaskPlan::taskId, task -> task));
        var refreshedTasks = stagePlanApi.listTaskPlans(tenantId, batch.getProjectId()).stream().filter(task -> participating.contains(task.stageCode()))
                .map(task -> { var prior = previousTasks.get(task.taskId()); return prior == null ? task : new ProjectStagePlanApi.TaskPlan(
                        task.taskId(), task.parentTaskId(), task.stageCode(), task.name(), task.version(), prior.planStart(), prior.planEnd(), task.acceptanceTime()); }).toList();
        batchUpdate.setTaskPlansJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(refreshedTasks));
        batchUpdate.setVersion(batch.getVersion());
        if (batchMapper.updateById(batchUpdate) != 1) throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
        return getBatch(batchId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StagePlanBatchRespVO updateItems(StagePlanItemUpdateReqVO updateReqVO) {
        StagePlanBatchDO batch = validateBatchEditable(updateReqVO.getId());
        if (!Objects.equals(batch.getVersion(), updateReqVO.getVersion())) {
            throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
        }
        List<StagePlanItemDO> items = itemMapper.selectListByBatchId(batch.getId());
        Map<Long, StagePlanItemUpdateReqVO.Item> updates = new LinkedHashMap<>();
        for (StagePlanItemUpdateReqVO.Item item : updateReqVO.getItems()) {
            if (item == null || item.getId() == null || updates.putIfAbsent(item.getId(), item) != null) {
                throw exception(STAGE_PLAN_ARGUMENT_INVALID, "明细编号不能为空或重复");
            }
        }
        if (!items.stream().map(StagePlanItemDO::getId)
                .collect(java.util.stream.Collectors.toSet()).containsAll(updates.keySet())) {
            throw exception(STAGE_PLAN_ARGUMENT_INVALID, "不能调整其他批次的阶段明细");
        }
        boolean datesChanged = items.stream().anyMatch(item -> {
            var change = updates.get(item.getId());
            return change != null && (!Objects.equals(item.getPlanStart(), change.getPlanStart()) || !Objects.equals(item.getPlanEnd(), change.getPlanEnd()));
        }) || updateReqVO.getTasks() != null && !Objects.equals(readTasks(batch), updateReqVO.getTasks());
        if (datesChanged && (updateReqVO.getRemark() == null || updateReqVO.getRemark().isBlank()))
            throw exception(STAGE_PLAN_ARGUMENT_INVALID, "调整日期必须填写原因");
        for (StagePlanItemDO item : items) {
            StagePlanItemUpdateReqVO.Item change = updates.get(item.getId());
            if (change == null) continue;
            item.setPlanStart(change.getPlanStart());
            item.setPlanEnd(change.getPlanEnd());
            if (change.getRemark() != null) item.setRemark(change.getRemark());
            StagePlanItemDO update = new StagePlanItemDO();
            update.setId(item.getId());
            update.setPlanStart(item.getPlanStart());
            update.setPlanEnd(item.getPlanEnd());
            update.setRemark(item.getRemark());
            itemMapper.updateById(update);
        }
        ConstructionPlanRevisionDO baseline = loadBatchBaseline(batch);
        validateNoOverlap(items, baseline, batch);
        validateAcceptanceConstraints(batch, items);
        var taskPlans = updateReqVO.getTasks() == null ? readTasks(batch) : updateReqVO.getTasks();
        validateTaskPlans(readTasks(batch), taskPlans, items, false);
        {
            StagePlanBatchDO batchUpdate = new StagePlanBatchDO();
            batchUpdate.setId(batch.getId());
            batchUpdate.setRemark(updateReqVO.getRemark());
            batchUpdate.setTaskPlansJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(taskPlans));
            batchUpdate.setVersion(batch.getVersion());
            if (batchMapper.updateById(batchUpdate) != 1) throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
        }
        return getBatch(batch.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StagePlanBatchRespVO submit(Long batchId, Long approverUserId) {
        if (approverUserId == null || approverUserId <= 0) {
            throw exception(STAGE_PLAN_ARGUMENT_INVALID, "审批人不能为空");
        }
        StagePlanBatchDO batch = validateBatchEditable(batchId);
        var approver = participants.inspect(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery(
                batch.getProjectId(), approverUserId, cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES, java.time.LocalDateTime.now()));
        if (approver == null || !Objects.equals(approver.userId(), approverUserId)
                || approver.effectiveRoleCodes().stream().noneMatch(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES::contains))
            throw exception(STAGE_PLAN_ARGUMENT_INVALID, "审批人必须是本项目的有效服务经理");
        List<StagePlanItemDO> items = itemMapper.selectListByBatchId(batchId);
        ConstructionPlanRevisionDO baseline = loadBatchBaseline(batch);
        validateNoOverlap(items, baseline, batch);
        validateAcceptanceConstraints(batch, items);
        validateTaskPlans(readTasks(batch), readTasks(batch), items, true);
        if (batch.getInputSnapshot() == null) throw exception(STAGE_PLAN_DATE_INVALID, "请先按冻结项目计划重新推算，再提交审核");
        verifyInputs(batch);
        String processKey = properties.getProcessDefinitionKey();
        if (processKey == null || processKey.isBlank()) {
            throw exception(STAGE_PLAN_BPM_CONFIG_INVALID);
        }
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        BpmProcessInstanceCreateReqDTO request = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(processKey)
                .setBusinessKey(String.valueOf(batch.getId()))
                .setVariables(new LinkedHashMap<>(Map.of("projectId", batch.getProjectId(),
                        "stagePlanBatchId", batch.getId())))
                .setStartUserSelectAssignees(Map.of(APPROVAL_TASK, List.of(approverUserId)));
        String processInstanceId = createProcessInstance(actorId, request);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw exception(STAGE_PLAN_BPM_CONFIG_INVALID);
        }
        StagePlanBatchDO update = new StagePlanBatchDO();
        update.setId(batch.getId());
        update.setStatus(StagePlanBatchDO.STATUS_PENDING_APPROVAL);
        update.setBpmProcessKey(processKey);
        update.setBpmProcessInstanceId(processInstanceId);
        update.setSubmittedAt(java.time.LocalDateTime.now());
        update.setSubmittedBy(actorId);
        update.setRejectReason(null);
        update.setVersion(batch.getVersion());
        if (batchMapper.updateById(update) != 1) throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
        return getBatch(batchId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleBpmResult(String processInstanceId, Integer bpmStatus, String reason) {
        BpmProcessInstanceStatusEnum terminal = resolveTerminal(bpmStatus);
        if (terminal == null) return;
        StagePlanBatchDO batch = batchMapper.selectByProcessInstanceId(processInstanceId);
        if (batch == null
                || !Objects.equals(batch.getBpmProcessInstanceId(), processInstanceId)
                || !Objects.equals(StagePlanBatchDO.STATUS_PENDING_APPROVAL, batch.getStatus())) {
            // 非本域流程回执或批次已终态（重复回执）时忽略
            log.info("阶段施工计划审批回执忽略：instanceId={}, batchStatus={}", processInstanceId,
                    batch == null ? null : batch.getStatus());
            return;
        }
        if (terminal == BpmProcessInstanceStatusEnum.APPROVE) {
            verifyInputs(batch);
            Long tenantId = batch.getTenantId();
            List<StagePlanItemDO> items = itemMapper.selectListByBatchId(batch.getId());
            // 生效基线：经受控写入契约回写阶段计划日期，不绕过 PROJ 所有权
            stagePlanApi.applyPlanDates(tenantId, batch.getProjectId(), items.stream()
                    .map(item -> new ProjectStagePlanApi.StagePlanDate(
                            item.getPhaseId(), item.getPlanStart(), item.getPlanEnd()))
                    .toList());
            stagePlanApi.applyTaskPlanDates(tenantId, batch.getProjectId(), readTasks(batch));
            // 工期与施工计划共同完成重算；保留并行工期变更，CAS 失败回滚整次生效。
            ConstructionPlanDO plan = constructionPlanMapper.selectByProjectId(tenantId, batch.getProjectId());
            if (plan == null || !Objects.equals(plan.getCurrentDurationRevisionId(), batch.getDurationRevisionId())) {
                throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
            }
            var recalculated = new cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate(
                    tenantId, plan.getId(), plan.getVersion(), plan.getCurrentDurationRevisionId(),
                    plan.getPendingChangeId(), ConstructionPlanDO.RECALCULATED, batch.getDurationRevisionId(),
                    Objects.toString(SecurityFrameworkUtils.getLoginUserId(), batch.getUpdater()));
            if (constructionPlanMapper.updateVersionIfMatch(recalculated) != 1) {
                throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
            }
            StagePlanBatchDO update = new StagePlanBatchDO();
            update.setId(batch.getId());
            update.setStatus(StagePlanBatchDO.STATUS_EFFECTIVE);
            update.setEffectiveAt(java.time.LocalDateTime.now());
            update.setRejectReason(null);
            update.setVersion(batch.getVersion());
            if (batchMapper.updateById(update) != 1) throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
        } else {
            // REJECT / CANCEL：驳回重提（调整后可再次提交）
            StagePlanBatchDO update = new StagePlanBatchDO();
            update.setId(batch.getId());
            update.setStatus(StagePlanBatchDO.STATUS_REJECTED);
            update.setRejectReason(reason != null && !reason.isBlank() ? reason
                    : (terminal == BpmProcessInstanceStatusEnum.CANCEL ? "审批流程被取消" : "审批被驳回"));
            update.setVersion(batch.getVersion());
            if (batchMapper.updateById(update) != 1) throw exception(STAGE_PLAN_VERSION_NOT_MATCH);
        }
    }

    @Override
    public StagePlanBatchRespVO getBatch(Long batchId) {
        StagePlanBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw exception(STAGE_PLAN_BATCH_NOT_EXISTS);
        }
        requireAccess(batch.getProjectId(), false);
        StagePlanBatchRespVO respVO = BeanUtils.toBean(batch, StagePlanBatchRespVO.class);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ConstructionPlanRevisionDO baseline = loadBatchBaseline(batch);
        if (baseline != null) {
            respVO.setBaselineStart(batch.getCalculatedStart() == null ? baseline.getStartDate() : batch.getCalculatedStart());
            respVO.setBaselineEnd(batch.getCalculatedEnd() == null ? baseline.getEndDate() : batch.getCalculatedEnd());
        }
        respVO.setTasks(readTasks(batch));
        respVO.setItems(itemMapper.selectListByBatchId(batchId).stream()
                .map(item -> BeanUtils.toBean(item, StagePlanItemRespVO.class)).toList());
        return respVO;
    }

    @Override
    public PageResult<StagePlanBatchRespVO> getBatchPage(StagePlanBatchPageReqVO pageReqVO) {
        var query = BeanUtils.toBean(pageReqVO, cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.query.StagePlanPageQuery.class);
        query.setVisibleProjectIds(visibleProjects("PROJECT_VIEW"));
        PageResult<StagePlanBatchDO> page = batchMapper.selectPage(query);
        return new PageResult<>(page.getList().stream()
                .map(batch -> BeanUtils.toBean(batch, StagePlanBatchRespVO.class)).toList(),
                page.getTotal());
    }

    @Override
    public List<StagePlanOverdueRowVO> getOverdueStages(Long projectId) {
        return computeOverdueRows(projectId).stream()
                .map(row -> BeanUtils.toBean(row, StagePlanOverdueRowVO.class)).toList();
    }

    @Override
    public StagePlanOverdueSummaryVO getOverdueSummary(Long projectId) {
        List<StagePlanOverdueRowVO> rows = getOverdueStages(projectId);
        StagePlanOverdueSummaryVO summary = new StagePlanOverdueSummaryVO();
        summary.setOverdueStageCount((long) rows.size());
        summary.setOverdueProjectCount(rows.stream()
                .map(StagePlanOverdueRowVO::getProjectId).distinct().count());
        return summary;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 超期计算（PLN-03）：仅以已生效计划批次为基准；已完成/已跳过阶段不参与，
     * 未完成阶段计划完成时间早于今天即超期，超期天数按日历日计。
     */
    private List<StagePlanOverdueRowVO> computeOverdueRows(Long projectId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var visible = visibleProjects("PROJECT_VIEW");
        List<StagePlanBatchDO> effectiveBatches = batchMapper.selectListByStatus(
                StagePlanBatchDO.STATUS_EFFECTIVE).stream()
                .filter(batch -> visible.contains(batch.getProjectId()) && (projectId == null || projectId.equals(batch.getProjectId())))
                .toList();
        if (effectiveBatches.isEmpty()) {
            return List.of();
        }
        // Historical effective batches remain immutable; only the latest approved version is current.
        var latest = new LinkedHashMap<Long, StagePlanBatchDO>();
        effectiveBatches.stream().sorted(java.util.Comparator.comparing(StagePlanBatchDO::getEffectiveAt,
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())).thenComparing(StagePlanBatchDO::getId))
                .forEach(value -> latest.put(value.getProjectId(), value));
        effectiveBatches = List.copyOf(latest.values());
        // 阶段完成状态经 PROJ 阶段计划契约读取；同一项目只查一次
        Map<Long, Map<Long, String>> projectPhaseStatuses = new LinkedHashMap<>();
        for (StagePlanBatchDO batch : effectiveBatches) {
            if (projectPhaseStatuses.containsKey(batch.getProjectId())) continue;
            Map<Long, String> statuses = new LinkedHashMap<>();
            for (ProjectStagePlanApi.StagePlanFact fact : stagePlanApi.listStages(tenantId,
                    batch.getProjectId())) {
                statuses.put(fact.stageId(), fact.status());
            }
            projectPhaseStatuses.put(batch.getProjectId(), statuses);
        }
        LocalDate today = LocalDate.now();
        List<StagePlanOverdueRowVO> rows = new ArrayList<>();
        for (StagePlanBatchDO batch : effectiveBatches) {
            Map<Long, String> statuses = projectPhaseStatuses.getOrDefault(batch.getProjectId(),
                    Map.of());
            for (StagePlanItemDO item : itemMapper.selectListByBatchId(batch.getId())) {
                if (item.getPlanEnd() == null || !item.getPlanEnd().isBefore(today)) continue;
                // 2 已完成 / 3 已跳过 不构成超期；状态未知按未完成保守计入
                String status = statuses.get(item.getPhaseId());
                if ("2".equals(status) || "3".equals(status)) continue;
                StagePlanOverdueRowVO row = new StagePlanOverdueRowVO();
                row.setProjectId(batch.getProjectId());
                row.setBatchId(batch.getId());
                row.setPhaseId(item.getPhaseId());
                row.setPhaseName(item.getPhaseName());
                row.setPlanEnd(item.getPlanEnd());
                row.setOverdueDays(ChronoUnit.DAYS.between(item.getPlanEnd(), today));
                row.setPhaseStatus(status == null ? null : Integer.valueOf(status));
                rows.add(row);
            }
        }
        rows.sort((a, b) -> Long.compare(
                b.getOverdueDays() == null ? 0 : b.getOverdueDays(),
                a.getOverdueDays() == null ? 0 : a.getOverdueDays()));
        return rows;
    }

    private void validateAcceptanceConstraints(StagePlanBatchDO batch, List<StagePlanItemDO> items) {
        if (batch.getInputSnapshot() == null) return;
        var input = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(batch.getInputSnapshot(), tools.jackson.databind.JsonNode.class);
        for (var stage : input.path("stages")) {
            String time = stage.path("acceptanceTime").asText("");
            if (time.isBlank()) continue;
            LocalDate deadline = LocalDate.parse(time.substring(0, 10));
            for (var item : items) if (Objects.equals(item.getPhaseId(), stage.path("stageId").asLong())
                    && item.getPlanEnd() != null && item.getPlanEnd().isAfter(deadline))
                throw exception(STAGE_PLAN_DATE_INVALID, item.getPhaseName() + " 计划结束不得晚于计划验收时间");
        }
    }

    private void verifyInputs(StagePlanBatchDO batch) {
        if (batch.getInputSnapshot() == null) throw exception(STAGE_PLAN_DATE_INVALID, "旧计划缺少推算输入快照，请重新推算");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(resolveBaselineRevisionId(tenantId, batch.getProjectId()), batch.getDurationRevisionId()))
            throw exception(STAGE_PLAN_DATE_INVALID, "生效工期已变化，请重新推算并审批");
        var baseline = loadBatchBaseline(batch);
        if (baseline == null) throw exception(STAGE_PLAN_DATE_INVALID, "计划工期版本缺失");
        ProjectStagePlanApi.ScheduleCalculation current;
        try { current = stagePlanApi.calculateSchedule(tenantId, batch.getProjectId(), baseline.getStartDate(), baseline.getEndDate()); }
        catch (IllegalArgumentException failure) { throw exception(STAGE_PLAN_DATE_INVALID, failure.getMessage()); }
        if (current == null || !Objects.equals(current.inputSnapshot(), batch.getInputSnapshot()))
            throw exception(STAGE_PLAN_DATE_INVALID, "回款验收时间、任务或冻结计划已变化，请重新推算并审批");
    }

    private List<ProjectStagePlanApi.TaskPlan> readTasks(StagePlanBatchDO batch) {
        return batch.getTaskPlansJson() == null ? List.of() : cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(
                batch.getTaskPlansJson(), ProjectStagePlanApi.TaskPlan.class);
    }

    private void validateTaskPlans(List<ProjectStagePlanApi.TaskPlan> original, List<ProjectStagePlanApi.TaskPlan> proposed,
                                   List<StagePlanItemDO> items, boolean requireDates) {
        var known = original.stream().collect(java.util.stream.Collectors.toMap(ProjectStagePlanApi.TaskPlan::taskId, task -> task));
        var stages = items.stream().collect(java.util.stream.Collectors.toMap(StagePlanItemDO::getPhaseCode, item -> item));
        var seen = new java.util.HashSet<Long>();
        for (var task : proposed) {
            var before = task == null ? null : known.get(task.taskId());
            if (before == null || !seen.add(task.taskId()) || !Objects.equals(before.stageCode(), task.stageCode())
                    || !Objects.equals(before.parentTaskId(), task.parentTaskId()) || !Objects.equals(before.version(), task.version())
                    || !Objects.equals(before.acceptanceTime(), task.acceptanceTime()) || !Objects.equals(before.name(), task.name()))
                throw exception(STAGE_PLAN_ARGUMENT_INVALID, "任务不属于当前计划或其冻结身份被修改");
            if (!requireDates && task.planStart() == null && task.planEnd() == null) continue;
            var stage = stages.get(task.stageCode());
            if (stage == null || task.planStart() == null || task.planEnd() == null || task.planEnd().isBefore(task.planStart())
                    || stage.getPlanStart() == null || stage.getPlanEnd() == null || task.planStart().isBefore(stage.getPlanStart())
                    || task.planEnd().isAfter(stage.getPlanEnd()) || task.acceptanceTime() != null && task.planEnd().isAfter(task.acceptanceTime()))
                throw exception(STAGE_PLAN_DATE_INVALID, "任务 " + task.name() + " 须安排在所属阶段内且不晚于计划验收时间");
        }
        if (!seen.equals(known.keySet())) throw exception(STAGE_PLAN_ARGUMENT_INVALID, "不得删除计划内任务");
    }

    private StagePlanBatchDO validateBatchEditable(Long batchId) {
        StagePlanBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw exception(STAGE_PLAN_BATCH_NOT_EXISTS);
        }
        requireAccess(batch.getProjectId(), true);
        if (!Objects.equals(StagePlanBatchDO.STATUS_DRAFT, batch.getStatus())
                && !Objects.equals(StagePlanBatchDO.STATUS_REJECTED, batch.getStatus())) {
            throw exception(STAGE_PLAN_STATUS_INVALID);
        }
        return batch;
    }

    private Long resolveBaselineRevisionId(Long tenantId, Long projectId) {
        ConstructionPlanRevisionDO baseline = loadBaseline(tenantId, projectId);
        return baseline == null ? null : baseline.getId();
    }

    private ConstructionPlanRevisionDO loadBaseline(Long tenantId, Long projectId) {
        ConstructionPlanDO plan = constructionPlanMapper.selectByProjectId(tenantId, projectId);
        if (plan == null || plan.getCurrentDurationRevisionId() == null) {
            return null;
        }
        return constructionPlanRevisionMapper.selectById(new ConstructionPlanRevisionLockQuery(
                tenantId, plan.getId(), plan.getCurrentDurationRevisionId()));
    }

    private ConstructionPlanRevisionDO loadBatchBaseline(StagePlanBatchDO batch) {
        if (batch.getDurationRevisionId() == null) return null;
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ConstructionPlanDO plan = constructionPlanMapper.selectByProjectId(tenantId, batch.getProjectId());
        if (plan == null) throw exception(STAGE_PLAN_DATE_INVALID, "计划引用的工期不存在");
        ConstructionPlanRevisionDO baseline = constructionPlanRevisionMapper.selectById(
                new ConstructionPlanRevisionLockQuery(tenantId, plan.getId(), batch.getDurationRevisionId()));
        if (baseline == null) throw exception(STAGE_PLAN_DATE_INVALID, "计划引用的工期版本不存在");
        return baseline;
    }

    /**
     * 阶段间不得重叠；有工期基线时计划必须落在基线窗口内。
     */
    private void validateNoOverlap(List<StagePlanItemDO> items, ConstructionPlanRevisionDO baseline, StagePlanBatchDO batch) {
        // A direct-sign acceptance deadline may shift the calculated window. Do not mutate the
        // duration revision: MyBatis can return that same instance during input verification.
        LocalDate windowStart = batch.getCalculatedStart() != null ? batch.getCalculatedStart()
                : baseline == null ? null : baseline.getStartDate();
        LocalDate windowEnd = batch.getCalculatedEnd() != null ? batch.getCalculatedEnd()
                : baseline == null ? null : baseline.getEndDate();
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getPlanStart() == null || item.getPlanEnd() == null)) {
            throw exception(STAGE_PLAN_DATE_INVALID, "请填写全部阶段的计划起止日期");
        }
        List<StagePlanItemDO> ordered = new ArrayList<>(items.stream()
                .filter(item -> item.getPlanStart() != null && item.getPlanEnd() != null).toList());
        ordered.sort((a, b) -> Integer.compare(a.getSort() == null ? 0 : a.getSort(),
                b.getSort() == null ? 0 : b.getSort()));
        for (StagePlanItemDO item : ordered) {
            if (item.getPlanEnd().isBefore(item.getPlanStart())) {
                throw exception(STAGE_PLAN_DATE_INVALID,
                        item.getPhaseName() + " 计划结束时间早于计划开始时间");
            }
            if (windowStart != null && windowEnd != null && (item.getPlanStart().isBefore(windowStart)
                    || item.getPlanEnd().isAfter(windowEnd))) {
                throw exception(STAGE_PLAN_DATE_INVALID,
                        item.getPhaseName() + " 计划时间超出工期基线窗口（"
                                + windowStart + " ~ " + windowEnd + "）");
            }
        }
        for (int i = 1; i < ordered.size(); i++) {
            StagePlanItemDO prev = ordered.get(i - 1);
            StagePlanItemDO current = ordered.get(i);
            if (!current.getPlanStart().isAfter(prev.getPlanEnd())) {
                throw exception(STAGE_PLAN_DATE_INVALID,
                        current.getPhaseName() + " 与前一阶段【" + prev.getPhaseName() + "】计划时间重叠");
            }
        }
    }

    private BpmProcessInstanceStatusEnum resolveTerminal(Integer bpmStatus) {
        if (Objects.equals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), bpmStatus)) {
            return BpmProcessInstanceStatusEnum.APPROVE;
        }
        if (Objects.equals(BpmProcessInstanceStatusEnum.REJECT.getStatus(), bpmStatus)) {
            return BpmProcessInstanceStatusEnum.REJECT;
        }
        if (Objects.equals(BpmProcessInstanceStatusEnum.CANCEL.getStatus(), bpmStatus)) {
            return BpmProcessInstanceStatusEnum.CANCEL;
        }
        return null;
    }

    private String createProcessInstance(Long actorId, BpmProcessInstanceCreateReqDTO request) {
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            return processInstanceApi.createProcessInstance(actorId, request);
        }
        Long trustedTenantId = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(null);
            return processInstanceApi.createProcessInstance(actorId, request);
        } finally {
            TenantContextHolder.setTenantId(trustedTenantId);
        }
    }
}
