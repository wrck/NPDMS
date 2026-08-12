package cn.iocoder.yudao.module.pms.engineering.service.jointtest;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.jointtest.vo.JointTestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.jointtest.JointTestDO;
import jakarta.validation.Valid;

/**
 * PMS 业务联调 Service 接口（FR-ENG-024）。
 * <p>
 * 联调编码在项目内唯一；状态变更必须使用 JointTestStatusRules 状态机校验。
 * 失败项不能静默通过，必须先关闭异常或创建问题单。
 */
public interface JointTestService {

    /**
     * 创建联调记录
     */
    Long createJointTest(@Valid JointTestSaveReqVO createReqVO);

    /**
     * 更新联调记录
     */
    void updateJointTest(@Valid JointTestSaveReqVO updateReqVO);

    /**
     * 删除联调记录
     */
    void deleteJointTest(Long id);

    /**
     * 查询联调详情
     */
    JointTestDO getJointTest(Long id);

    /**
     * 校验联调记录存在
     */
    JointTestDO validateJointTestExists(Long id);

    /**
     * 分页查询联调记录
     */
    PageResult<JointTestDO> getJointTestPage(JointTestPageReqVO pageReqVO);

    /**
     * 开始联调（0待联调 → 1进行中）
     */
    void start(Long id);

    /**
     * 联调通过（1进行中 → 2通过）
     */
    void pass(Long id);

    /**
     * 联调失败（1进行中 → 3失败），必须记录异常
     */
    void fail(Long id, String exceptionRecord);
}
