package cn.iocoder.yudao.module.pms.engineering.service.jointtest;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.query.JointTestPageQuery;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.domain.JointTestStatusRules;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 业务联调 Service 实现（FR-ENG-024）。
 * <p>
 * 失败项不能静默通过：调用 fail 时必须携带异常记录，由调用方/前端联动创建问题单。
 */
@Service
@Validated
@Slf4j
public class JointTestServiceImpl implements JointTestService {

    @Resource
    private ProjectScopeApi projectScopeApi;

    @Resource
    private cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi deviceSelectionApi;

    @Resource
    private JointTestMapper jointTestMapper;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long createJointTest(JointTestSaveReqVO createReqVO) {
        requireManage(createReqVO.getProjectId());
        if (createReqVO.getEquipmentId() != null) {
            deviceSelectionApi.validateSelection(createReqVO.getProjectId(), java.util.List.of(createReqVO.getEquipmentId()));
        }
        JointTestDO entity = BeanUtils.toBean(createReqVO, JointTestDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.JOINT_TEST, jointTestMapper));
        entity.setStatus(EngStatusEnum.JOINT_TEST_PENDING);
        jointTestMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateJointTest(JointTestSaveReqVO updateReqVO) {
        JointTestDO existing = validateJointTestExists(updateReqVO.getId());
        requireManage(existing.getProjectId());
        if (updateReqVO.getProjectId() != null && !Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())) throw exception(FORBIDDEN);
        updateReqVO.setProjectId(existing.getProjectId());
        Long equipmentId = updateReqVO.getEquipmentId() != null ? updateReqVO.getEquipmentId() : existing.getEquipmentId();
        if (equipmentId != null) {
            deviceSelectionApi.validateSelection(updateReqVO.getProjectId(), java.util.List.of(equipmentId));
        }
        if (JointTestStatusRules.isTerminal(existing.getStatus())) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        if (updateReqVO.getVersion() != null && !Objects.equals(existing.getVersion(), updateReqVO.getVersion().longValue())) {
            throw exception(JOINT_TEST_VERSION_NOT_MATCH);
        }
        JointTestDO update = BeanUtils.toBean(updateReqVO, JointTestDO.class);
        update.setStatus(existing.getStatus());
        update.setVersion(existing.getVersion());
        updateRecord(update);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteJointTest(Long id) {
        JointTestDO entity = validateJointTestExists(id);
        requireManage(entity.getProjectId());
        if (JointTestStatusRules.isTerminal(entity.getStatus())) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        jointTestMapper.deleteById(id);
    }

    @Override
    public JointTestDO getJointTest(Long id) {
        JointTestDO entity = validateJointTestExists(id);
        requireView(entity.getProjectId());
        return entity;
    }

    @Override
    public JointTestDO validateJointTestExists(Long id) {
        JointTestDO entity = jointTestMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(JOINT_TEST_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<JointTestDO> getJointTestPage(JointTestPageReqVO pageReqVO) {
        Set<Long> visible = projectScopeApi.resolveAllCurrent(new ProjectAllScopeQuery(
                TenantContextHolder.getRequiredTenantId(), actorId(), ProjectScopeApi.ACTION_VIEW));
        if (visible == null || visible.isEmpty() || pageReqVO.getProjectId() != null && !visible.contains(pageReqVO.getProjectId())) return PageResult.empty();
        JointTestPageQuery query = BeanUtils.toBean(pageReqVO, JointTestPageQuery.class);
        query.setVisibleProjectIds(Set.copyOf(visible));
        return jointTestMapper.selectPage(query);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        JointTestDO entity = validateJointTestExists(id);
        requireManage(entity.getProjectId());
        requireTransition(entity, JointTestStatusRules.Action.START);
        updateStatus(id, JointTestStatusRules.Action.START, entity.getVersion());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void pass(Long id) {
        JointTestDO entity = validateJointTestExists(id);
        requireManage(entity.getProjectId());
        requireTransition(entity, JointTestStatusRules.Action.PASS);
        updateStatus(id, JointTestStatusRules.Action.PASS, entity.getVersion());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void fail(Long id, String exceptionRecord) {
        if (StringUtils.isBlank(exceptionRecord)) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        JointTestDO entity = validateJointTestExists(id);
        requireManage(entity.getProjectId());
        requireTransition(entity, JointTestStatusRules.Action.FAIL);
        JointTestDO update = new JointTestDO();
        update.setId(id);
        update.setStatus(JointTestStatusRules.targetStatus(JointTestStatusRules.Action.FAIL));
        update.setExceptionRecord(exceptionRecord.trim());
        update.setVersion(entity.getVersion());
        updateRecord(update);
    }

    private void updateStatus(Long id, JointTestStatusRules.Action action, Long version) {
        JointTestDO update = new JointTestDO();
        update.setId(id);
        update.setStatus(JointTestStatusRules.targetStatus(action));
        update.setVersion(version);
        updateRecord(update);
    }

    private void requireTransition(JointTestDO entity, JointTestStatusRules.Action action) {
        try {
            JointTestStatusRules.requireTransition(entity.getStatus(), action);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
    }

    private void updateRecord(JointTestDO update) {
        if (jointTestMapper.updateById(update) != 1) {
            throw exception(JOINT_TEST_VERSION_NOT_MATCH);
        }
    }

    private Long actorId() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0) throw exception(FORBIDDEN);
        return actor;
    }

    private void requireView(Long projectId) {
        requireScope(ProjectCurrentScopeQuery.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                .subjectUserId(actorId()).anchorProjectId(projectId).actionCode(ProjectScopeApi.ACTION_VIEW).build());
    }

    private void requireManage(Long projectId) {
        requireScope(ProjectCurrentScopeQuery.builder().tenantId(TenantContextHolder.getRequiredTenantId())
                .subjectUserId(actorId()).anchorProjectId(projectId).actionCode(ProjectScopeApi.ACTION_MANAGE).build());
    }

    private void requireScope(ProjectCurrentScopeQuery query) {
        if (query.anchorProjectId() == null) throw exception(FORBIDDEN);
        var scope = projectScopeApi.resolveCurrent(query);
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(query.anchorProjectId())) throw exception(FORBIDDEN);
        if (ProjectScopeApi.ACTION_MANAGE.equals(query.actionCode())) {
            var locked = projectScopeApi.lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                    .tenantId(query.tenantId()).subjectUserId(query.subjectUserId()).anchorProjectId(query.anchorProjectId())
                    .actionCode(query.actionCode()).expectedScopeVersion(scope.treeVersion()).build());
            if (locked == null || !Objects.equals(scope.treeVersion(), locked.treeVersion()) || locked.fullProjectIds() == null
                    || !locked.fullProjectIds().contains(query.anchorProjectId())) throw exception(FORBIDDEN);
        }
    }

}
