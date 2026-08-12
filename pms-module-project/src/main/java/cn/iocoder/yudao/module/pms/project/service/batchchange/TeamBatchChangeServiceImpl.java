package cn.iocoder.yudao.module.pms.project.service.batchchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangeSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectteam.ProjectTeamMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange.TeamBatchChangeItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange.TeamBatchChangeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectteam.ProjectTeamMemberMapper;
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
 * 创建批次时按源用户与范围生成明细；执行时逐条更新团队成员 user_id，
 * 部分失败时批次状态为部分成功(2)，明细逐条返回成功/失败结果与原因。
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
    private ProjectTeamMemberMapper projectTeamMemberMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    @Transactional
    public Long createBatchChange(TeamBatchChangeSaveReqVO createReqVO) {
        // 1. 校验源/目标用户不同
        if (createReqVO.getSourceUserId().equals(createReqVO.getTargetUserId())) {
            throw exception(TEAM_BATCH_CHANGE_SOURCE_EQUALS_TARGET);
        }
        // 2. 生成批次编号
        String batchNo = generateBatchNo();
        // 3. 查询源用户的团队成员记录
        List<ProjectTeamMemberDO> sourceMembers = selectSourceMembers(
                createReqVO.getSourceUserId(), createReqVO.getScopeType(), createReqVO.getProjectIds());
        if (sourceMembers.isEmpty()) {
            throw exception(TEAM_BATCH_CHANGE_NO_ITEMS);
        }
        // 4. 批量查询项目名称（冗余到明细）
        Set<Long> projectIds = sourceMembers.stream()
                .map(ProjectTeamMemberDO::getProjectId).collect(Collectors.toSet());
        Map<Long, String> projectNameMap = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectByIds(projectIds).stream()
                .collect(Collectors.toMap(ProjectDO::getId, ProjectDO::getName));
        // 5. 写入批次
        TeamBatchChangeDO batch = BeanUtils.toBean(createReqVO, TeamBatchChangeDO.class);
        batch.setBatchNo(batchNo);
        batch.setStatus(STATUS_PROCESSING);
        batch.setTotalCount(sourceMembers.size());
        batch.setSuccessCount(0);
        batch.setFailureCount(0);
        batchChangeMapper.insert(batch);
        // 6. 写入明细（待处理）
        for (ProjectTeamMemberDO member : sourceMembers) {
            TeamBatchChangeItemDO item = new TeamBatchChangeItemDO();
            item.setBatchId(batch.getId());
            item.setProjectId(member.getProjectId());
            item.setProjectName(projectNameMap.get(member.getProjectId()));
            item.setTeamMemberId(member.getId());
            item.setBeforeRole(member.getRoleCode());
            item.setAfterRole(member.getRoleCode());
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
        // 逐条处理：每条独立事务边界由外层 @Transactional 保证，但单条失败不中断整体
        for (TeamBatchChangeItemDO item : items) {
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
     * 处理单条明细：将团队成员 user_id 由源用户更新为目标用户，保留角色不变。
     * 若目标用户在同项目已存在相同角色，抛异常标记失败（避免唯一约束冲突）。
     */
    private void processOneItem(TeamBatchChangeDO batch, TeamBatchChangeItemDO item) {
        ProjectTeamMemberDO member = projectTeamMemberMapper.selectById(item.getTeamMemberId());
        if (member == null) {
            throw new IllegalStateException("团队成员记录不存在");
        }
        // 校验目标用户在同项目同角色是否已存在（避免唯一约束冲突）
        ProjectTeamMemberDO duplicate = projectTeamMemberMapper
                .selectByProjectIdAndUserIdAndRoleCode(member.getProjectId(),
                        batch.getTargetUserId(), member.getRoleCode());
        if (duplicate != null) {
            throw new IllegalStateException("目标用户在该项目已存在相同角色");
        }
        // 更新 user_id（角色保持不变，完成角色移交）
        ProjectTeamMemberDO update = new ProjectTeamMemberDO();
        update.setId(member.getId());
        update.setUserId(batch.getTargetUserId());
        projectTeamMemberMapper.updateById(update);
    }

    /**
     * 按范围查询源用户的团队成员记录。
     */
    private List<ProjectTeamMemberDO> selectSourceMembers(Long sourceUserId, String scopeType, List<Long> projectIds) {
        LambdaQueryWrapperX<ProjectTeamMemberDO> wrapper = new LambdaQueryWrapperX<ProjectTeamMemberDO>()
                .eq(ProjectTeamMemberDO::getUserId, sourceUserId);
        if ("SELECTED".equalsIgnoreCase(scopeType) && projectIds != null && !projectIds.isEmpty()) {
            wrapper.in(ProjectTeamMemberDO::getProjectId, new HashSet<>(projectIds));
        }
        return projectTeamMemberMapper.selectList(wrapper);
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
