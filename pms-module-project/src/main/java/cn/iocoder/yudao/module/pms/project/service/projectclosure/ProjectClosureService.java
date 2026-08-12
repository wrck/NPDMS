package cn.iocoder.yudao.module.pms.project.service.projectclosure;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosurePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosureSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;

/**
 * 项目闭环审批 Service 接口
 */
public interface ProjectClosureService {

    /**
     * 创建项目闭环
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProjectClosure(ProjectClosureSaveReqVO createReqVO);

    /**
     * 更新项目闭环
     *
     * @param updateReqVO 更新信息
     */
    void updateProjectClosure(ProjectClosureSaveReqVO updateReqVO);

    /**
     * 删除项目闭环
     *
     * @param id 编号
     */
    void deleteProjectClosure(Long id);

    /**
     * 获得项目闭环分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<ProjectClosureDO> getProjectClosurePage(ProjectClosurePageReqVO pageReqVO);

    /**
     * 获得项目闭环
     *
     * @param id 编号
     * @return 项目闭环
     */
    ProjectClosureDO getProjectClosure(Long id);

    /**
     * 提交（0草稿 → 1待审批）
     *
     * @param id 编号
     */
    void submitProjectClosure(Long id);

    /**
     * 开始审批（1待审批 → 2审批中）
     *
     * @param id 编号
     */
    void startApprove(Long id);

    /**
     * 通过（2审批中 → 3已通过）
     * 门禁：校验 阶段完成 + 验收通过 + 问题关闭 + 审批完成
     *
     * @param id 编号
     */
    void passProjectClosure(Long id);

    /**
     * 驳回（2审批中 → 4已驳回）
     *
     * @param id 编号
     */
    void rejectProjectClosure(Long id);

    /**
     * 归档（3已通过 → 5已归档）
     *
     * @param id 编号
     */
    void archiveProjectClosure(Long id);

}
