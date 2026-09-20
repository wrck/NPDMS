package cn.iocoder.yudao.module.pms.project.service.batchchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange.TeamBatchChangeItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange.TeamBatchChangeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberIdentityQuery;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/**
 * PMS 团队批量变更 Service 实现（FR-PROJ-014）。
 * <p>
 * 项目主档引用新权威 {@code proj_project}（AI-MIG-000 / V260 全量前向导入）；
 * 旧 {@code pms_project_retired} 已冻结只读。自有批次表 {@code proj_team_batch_change*}
 * 为 V254 CURRENT_FORWARD 当前承载。成员数据读写统一走新区间制成员载体
 * {@code proj_project_member_assignment}（旧 {@code pms_project_team_member_retired}
 * 已随 pms_ 旧域退役为只读）；移交语义为关闭源成员区间 + 插入目标用户新区间，
 * 遵循 OrdinaryProjectMemberService 的写入惯例（version 0、changeReason、生效窗口）。
 * <p>
 * 创建批次时按源用户有效区间生成明细；执行时逐条完成区间移交，
 * 部分失败时批次状态为部分成功(2)，明细逐条返回成功/失败结果与原因；
 * 已成功明细在失败重试时直接计为成功，不重复移交。
 */
@Service
@Validated
@Slf4j
public class TeamBatchChangeServiceImpl implements TeamBatchChangeService {

    /** 批次状态：处理中 */
    private static final int STATUS_PROCESSING = 0;
    /** 批次状态：成功 */
    private static final int STATUS_SUCCESS = 1;
    /** 批次状态：部分成功 */
    private static final int STATUS_PARTIAL = 2;
    /** 批次状态：失败 */
    private static final int STATUS_FAILED = 3;

    /** 明细状态：待处理 */
    private static final int ITEM_PENDING = 0;
    /** 明细状态：成功 */
    private static final int ITEM_SUCCESS = 1;
    /** 明细状态：失败 */
    private static final int ITEM_FAILURE = 2;

    @Resource
    private TeamBatchChangeMapper batchChangeMapper;
    @Resource
    private TeamBatchChangeItemMapper batchChangeItemMapper;
    @Resource
    private ProjectMemberAssignmentMapper memberAssignmentMapper;
    @Resource(name = "projectMasterMapper")
    private ProjectMasterMapper projectMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    @Override
    @Transactional
    public Long createBatchChange(TeamBatchChangeSaveReqVO createReqVO) {
        // 1. 校验源/目标用户不同
        if (createReqVO.getSourceUserId().equals(createReqVO.getTargetUserId())) {
            throw exception(TEAM_BATCH_CHANGE_SOURCE_EQUALS_TARGET);
        }
        // 2. 生成批次编号
        String batchNo = generateBatchNo();
        // 3. 查询源用户的有效成员区间
        List<ProjectMemberAssignmentDO> sourceMembers = selectSourceMembers(
                createReqVO.getSourceUserId(), createReqVO.getScopeType(), createReqVO.getProjectIds());
        if (sourceMembers.isEmpty()) {
            throw exception(TEAM_BATCH_CHANGE_NO_ITEMS);
        }
        // 4. 批量查询项目名称（冗余到明细）
        Set<Long> projectIds = sourceMembers.stream()
                .map(ProjectMemberAssignmentDO::getProjectId).collect(Collectors.toSet());
        Map<Long, String> projectNameMap = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectByIds(projectIds).stream()
                .collect(Collectors.toMap(ProjectMasterDO::getId, ProjectMasterDO::getProjectName));
        // 5. 写入批次
        TeamBatchChangeDO batch = BeanUtils.toBean(createReqVO, TeamBatchChangeDO.class);
        batch.setBatchNo(batchNo);
        batch.setStatus(STATUS_PROCESSING);
        batch.setTotalCount(sourceMembers.size());
        batch.setSuccessCount(0);
        batch.setFailureCount(0);
        batchChangeMapper.insert(batch);
        // 6. 写入明细（待处理）
        for (ProjectMemberAssignmentDO member : sourceMembers) {
            TeamBatchChangeItemDO item = new TeamBatchChangeItemDO();
            item.setBatchId(batch.getId());
            item.setProjectId(member.getProjectId());
            item.setProjectName(projectNameMap.get(member.getProjectId()));
            item.setTeamMemberId(member.getId());
            item.setBeforeRole(member.getMemberRole());
            item.setAfterRole(member.getMemberRole());
            item.setStatus(ITEM_PENDING);
            batchChangeItemMapper.insert(item);
        }
        return batch.getId();
    }

    @Override
    @Transactional
    public void updateBatchChange(TeamBatchChangeSaveReqVO updateReqVO) {
        TeamBatchChangeDO existing = validateBatchChangeExists(updateReqVO.getId());
        // 仅处理中/失败状态可改
        if (existing.getStatus() != null
                && existing.getStatus() != STATUS_PROCESSING && existing.getStatus() != STATUS_FAILED) {
            throw exception(TEAM_BATCH_CHANGE_STATUS_INVALID);
        }
        if (updateReqVO.getSourceUserId().equals(updateReqVO.getTargetUserId())) {
            throw exception(TEAM_BATCH_CHANGE_SOURCE_EQUALS_TARGET);
        }
        TeamBatchChangeDO update = BeanUtils.toBean(updateReqVO, TeamBatchChangeDO.class);
        batchChangeMapper.updateById(update);
    }

    @Override
    @Transactional
    public void deleteBatchChange(Long id) {
        validateBatchChangeExists(id);
        batchChangeItemMapper.deleteByBatchId(id);
        batchChangeMapper.deleteById(id);
    }

    @Override
    public TeamBatchChangeDO getBatchChange(Long id) {
        return batchChangeMapper.selectById(id);
    }

    @Override
    public PageResult<TeamBatchChangeDO> getBatchChangePage(TeamBatchChangePageReqVO pageReqVO) {
        return batchChangeMapper.selectPage(pageReqVO);
    }

    @Override
    public List<TeamBatchChangeItemDO> getBatchChangeItems(Long batchId) {
        return batchChangeItemMapper.selectListByBatchId(batchId);
    }

    @Override
    @Transactional
    public List<TeamBatchChangeItemDO> executeBatchChange(Long batchId) {
        TeamBatchChangeDO batch = validateBatchChangeExists(batchId);
        // 仅处理中/失败状态可执行（支持失败重试）
        if (batch.getStatus() != null
                && batch.getStatus() != STATUS_PROCESSING && batch.getStatus() != STATUS_FAILED) {
            throw exception(TEAM_BATCH_CHANGE_STATUS_INVALID);
        }
        List<TeamBatchChangeItemDO> items = batchChangeItemMapper.selectListByBatchId(batchId);
        int successCount = 0;
        int failureCount = 0;
        // 逐条处理：单条失败不中断整体；已成功明细不重复移交（区间制载体下重复执行会生成重复目标区间）
        for (TeamBatchChangeItemDO item : items) {
            if (item.getStatus() != null && item.getStatus() == ITEM_SUCCESS) {
                successCount++;
                continue;
            }
            try {
                processOneItem(batch, item);
                item.setStatus(ITEM_SUCCESS);
                successCount++;
            } catch (Exception e) {
                item.setStatus(ITEM_FAILURE);
                item.setErrorMessage(StringUtils.left(e.getMessage(), 500));
                failureCount++;
                log.warn("[executeBatchChange][批次 {} 明细 {} 处理失败：{}]",
                        batchId, item.getId(), e.getMessage());
            }
            batchChangeItemMapper.updateById(item);
        }
        // 汇总批次状态
        int status;
        if (failureCount == 0) {
            status = STATUS_SUCCESS;
        } else if (successCount == 0) {
            status = STATUS_FAILED;
        } else {
            status = STATUS_PARTIAL;
        }
        TeamBatchChangeDO update = new TeamBatchChangeDO();
        update.setId(batchId);
        update.setStatus(status);
        update.setSuccessCount(successCount);
        update.setFailureCount(failureCount);
        update.setVersion(batch.getVersion());
        batchChangeMapper.updateById(update);
        return items;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 处理单条明细：将成员区间移交目标用户，角色保持不变。
     * 区间制载体 proj_project_member_assignment 的移交语义为
     * 插入目标用户新区间（version 0、changeReason、当前时间起生效）后关闭源区间；
     * 先插入后关闭，插入失败（并发冲突）时源区间保持不变。
     * 若目标用户在同项目已有同角色有效区间，抛异常标记失败（避免重复成员）。
     */
    private void processOneItem(TeamBatchChangeDO batch, TeamBatchChangeItemDO item) {
        ProjectMemberAssignmentDO source = memberAssignmentMapper.selectById(item.getTeamMemberId());
        if (source == null) {
            throw new IllegalStateException("团队成员记录不存在");
        }
        String reason = "团队批量变更批次 " + batch.getBatchNo();
        LocalDateTime now = LocalDateTime.now();
        boolean sourceActive = "ACTIVE".equals(source.getStatus())
                && (source.getEffectiveTo() == null || source.getEffectiveTo().isAfter(now));
        adminUserApi.validateUser(batch.getTargetUserId());
        // 锁定目标用户在同项目同角色的有效区间（防并发重复移交）
        List<ProjectMemberAssignmentDO> targetActive = memberAssignmentMapper.selectActiveMemberIdentityForUpdate(
                new ProjectMemberIdentityQuery(source.getTenantId(), source.getProjectId(),
                        batch.getTargetUserId(), source.getMemberRole(), now));
        if (!targetActive.isEmpty()) {
            boolean created = targetActive.stream()
                    .anyMatch(row -> reason.equals(row.getChangeReason()));
            if (created && sourceActive) {
                // 上次执行已插入目标区间但关闭源区间失败：补完成关闭（幂等续跑）
                closeAssignment(source, reason, now);
                return;
            }
            if (created) {
                return;
            }
            throw new IllegalStateException("目标用户在该项目已存在相同角色");
        }
        if (!sourceActive) {
            throw new IllegalStateException("源成员记录已失效");
        }
        // 先插入目标区间再关闭源区间
        ProjectMemberAssignmentDO target = new ProjectMemberAssignmentDO();
        target.setTenantId(source.getTenantId());
        target.setProjectId(source.getProjectId());
        target.setUserId(batch.getTargetUserId());
        AdminUserRespDTO targetUser = adminUserApi.getUser(batch.getTargetUserId());
        target.setMemberName(targetUser == null ? null : targetUser.getNickname());
        if (targetUser != null && targetUser.getDeptId() != null) {
            target.setDepartmentId(targetUser.getDeptId());
            DeptRespDTO dept = deptApi.getDept(targetUser.getDeptId());
            if (dept != null) {
                target.setDepartmentCode(dept.getCode());
                target.setDepartmentName(dept.getName());
            }
        }
        target.setMemberRole(source.getMemberRole());
        target.setAssignmentType(source.getAssignmentType());
        target.setSiteId(source.getSiteId());
        target.setResponsibility(source.getResponsibility());
        target.setRemark(source.getRemark());
        target.setChangeReason(reason);
        target.setEffectiveFrom(now);
        target.setStatus("ACTIVE");
        target.setVersion(0);
        if (memberAssignmentMapper.insert(target) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        closeAssignment(source, reason, now);
    }

    /**
     * 关闭成员区间：生效至当前时间并记录原因，口径同 OrdinaryProjectMemberService#close。
     */
    private void closeAssignment(ProjectMemberAssignmentDO member, String reason, LocalDateTime now) {
        ProjectMemberAssignmentDO close = new ProjectMemberAssignmentDO();
        close.setId(member.getId());
        close.setEffectiveTo(now);
        close.setEndReason(reason);
        close.setVersion(member.getVersion());
        if (memberAssignmentMapper.updateById(close) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
    }

    /**
     * 按范围查询源用户的有效成员区间（状态 ACTIVE 且当前时间落在生效窗口内）。
     */
    private List<ProjectMemberAssignmentDO> selectSourceMembers(Long sourceUserId, String scopeType, List<Long> projectIds) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapperX<ProjectMemberAssignmentDO> wrapper = new LambdaQueryWrapperX<ProjectMemberAssignmentDO>()
                .eq(ProjectMemberAssignmentDO::getUserId, sourceUserId)
                .eq(ProjectMemberAssignmentDO::getStatus, "ACTIVE");
        wrapper.and(w -> w.isNull(ProjectMemberAssignmentDO::getEffectiveFrom)
                .or().le(ProjectMemberAssignmentDO::getEffectiveFrom, now));
        wrapper.and(w -> w.isNull(ProjectMemberAssignmentDO::getEffectiveTo)
                .or().gt(ProjectMemberAssignmentDO::getEffectiveTo, now));
        if ("SELECTED".equalsIgnoreCase(scopeType) && projectIds != null && !projectIds.isEmpty()) {
            wrapper.in(ProjectMemberAssignmentDO::getProjectId, new HashSet<>(projectIds));
        }
        return memberAssignmentMapper.selectList(wrapper);
    }

    /**
     * 生成全局唯一批次编号：BC + yyyyMMddHHmmss + 4 位随机数。
     */
    private String generateBatchNo() {
        return "BC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private TeamBatchChangeDO validateBatchChangeExists(Long id) {
        TeamBatchChangeDO batch = batchChangeMapper.selectById(id);
        if (batch == null) {
            throw exception(TEAM_BATCH_CHANGE_NOT_EXISTS);
        }
        return batch;
    }

}
