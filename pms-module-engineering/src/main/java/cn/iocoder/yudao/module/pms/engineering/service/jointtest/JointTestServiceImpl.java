package cn.iocoder.yudao.module.pms.engineering.service.jointtest;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.jointtest.JointTestMapper;
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
    private JointTestMapper jointTestMapper;

    @Override
    public Long createJointTest(JointTestSaveReqVO createReqVO) {
        validateCodeUniqueInProject(null, createReqVO.getProjectId(), createReqVO.getCode());
        JointTestDO entity = BeanUtils.toBean(createReqVO, JointTestDO.class);
        entity.setStatus(EngStatusEnum.JOINT_TEST_PENDING);
        jointTestMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateJointTest(JointTestSaveReqVO updateReqVO) {
        JointTestDO existing = validateJointTestExists(updateReqVO.getId());
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(JOINT_TEST_CODE_DUPLICATE, updateReqVO.getCode());
        }
        if (JointTestStatusRules.isTerminal(existing.getStatus())) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        JointTestDO update = BeanUtils.toBean(updateReqVO, JointTestDO.class);
        jointTestMapper.updateById(update);
    }

    @Override
    public void deleteJointTest(Long id) {
        validateJointTestExists(id);
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
    public void start(Long id) {
        JointTestDO entity = validateJointTestExists(id);
        JointTestStatusRules.requireTransition(entity.getStatus(), JointTestStatusRules.Action.START);
        updateStatus(id, JointTestStatusRules.Action.START, entity.getVersion());
    }

    @Override
    public void pass(Long id) {
        JointTestDO entity = validateJointTestExists(id);
        JointTestStatusRules.requireTransition(entity.getStatus(), JointTestStatusRules.Action.PASS);
        updateStatus(id, JointTestStatusRules.Action.PASS, entity.getVersion());
    }

    @Override
    public void fail(Long id, String exceptionRecord) {
        if (StringUtils.isBlank(exceptionRecord)) {
            throw exception(JOINT_TEST_STATUS_INVALID);
        }
        JointTestDO entity = validateJointTestExists(id);
        JointTestStatusRules.requireTransition(entity.getStatus(), JointTestStatusRules.Action.FAIL);
        JointTestDO update = new JointTestDO();
        update.setId(id);
        update.setStatus(JointTestStatusRules.targetStatus(JointTestStatusRules.Action.FAIL));
        update.setExceptionRecord(exceptionRecord);
        update.setVersion(entity.getVersion());
        jointTestMapper.updateById(update);
    }

    private void updateStatus(Long id, JointTestStatusRules.Action action, Integer version) {
        JointTestDO update = new JointTestDO();
        update.setId(id);
        update.setStatus(JointTestStatusRules.targetStatus(action));
        update.setVersion(version);
        jointTestMapper.updateById(update);
    }

    private void validateCodeUniqueInProject(Long id, Long projectId, String code) {
        if (StringUtils.isBlank(code) || projectId == null) {
            return;
        }
        JointTestDO existing = jointTestMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(JOINT_TEST_CODE_DUPLICATE, code);
        }
    }
}
