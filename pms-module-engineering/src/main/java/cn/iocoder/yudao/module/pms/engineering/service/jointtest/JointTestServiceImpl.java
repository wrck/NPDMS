package cn.iocoder.yudao.module.pms.engineering.service.jointtest;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.domain.JointTestStatusRules;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentKind;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.NativeAttachmentRegistration;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.attachment.query.NativeAttachmentOwnerLockQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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
    private NativeAttachmentRegistration attachments;


    @Resource
    private cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi deviceSelectionApi;

    @Resource
    private JointTestMapper jointTestMapper;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long createJointTest(JointTestSaveReqVO createReqVO) {
        attachments.requireLegacyUnchanged(null,createReqVO.getEvidenceUrl());
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
        JointTestDO existing = lockJointTest(updateReqVO.getId());
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
        attachments.requireLegacyUnchanged(existing.getEvidenceUrl(),updateReqVO.getEvidenceUrl());
        attachments.requireSameProject(NativeAttachmentKind.JOINT_TEST,existing.getId(),existing.getProjectId(),updateReqVO.getProjectId());
        JointTestDO update = BeanUtils.toBean(updateReqVO, JointTestDO.class);
        update.setStatus(existing.getStatus());
        update.setVersion(existing.getVersion());
        updateRecord(update);
        attachments.register(NativeAttachmentKind.JOINT_TEST,update.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteJointTest(Long id) {
        JointTestDO entity = lockJointTest(id);
        if (JointTestStatusRules.isTerminal(entity.getStatus())) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        jointTestMapper.deleteById(id);
    }

    @Override
    public JointTestDO getJointTest(Long id) {
        return jointTestMapper.selectById(id);
    }

    @Override
    public JointTestDO validateJointTestExists(Long id) {
        JointTestDO entity = jointTestMapper.selectById(id);
        if (entity == null) {
            throw exception(JOINT_TEST_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<JointTestDO> getJointTestPage(JointTestPageReqVO pageReqVO) {
        return jointTestMapper.selectPage(pageReqVO);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        JointTestDO entity = lockJointTest(id);
        requireTransition(entity, JointTestStatusRules.Action.START);
        updateStatus(id, JointTestStatusRules.Action.START, entity.getVersion());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void pass(Long id) {
        JointTestDO entity = lockJointTest(id);
        requireTransition(entity, JointTestStatusRules.Action.PASS);
        attachments.register(NativeAttachmentKind.JOINT_TEST,id);
        updateStatus(id, JointTestStatusRules.Action.PASS, entity.getVersion());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void fail(Long id, String exceptionRecord) {
        if (StringUtils.isBlank(exceptionRecord)) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        JointTestDO entity = lockJointTest(id);
        requireTransition(entity, JointTestStatusRules.Action.FAIL);
        JointTestDO update = new JointTestDO();
        update.setId(id);
        update.setStatus(JointTestStatusRules.targetStatus(JointTestStatusRules.Action.FAIL));
        attachments.register(NativeAttachmentKind.JOINT_TEST,id);
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

    private JointTestDO lockJointTest(Long id) {
        var row=jointTestMapper.selectAttachmentOwnerForUpdate(new NativeAttachmentOwnerLockQuery(TenantContextHolder.getRequiredTenantId(),id));
        if(row==null)throw exception(JOINT_TEST_NOT_EXISTS);
        return row;
    }

}
