package cn.iocoder.yudao.module.pms.project.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;

/**
 * PMS 项目 Service 接口（旧链只读过渡，F-PM01 存量冻结）
 * <p>
 * 写方法已随 F-PM01 退役（新链 {@code ProjectManualCreationService} 承接）；
 * 本接口仅保留 get/page 只读查询，旧 pms_project 数据冻结待 AI-MIG-000。
 */
public interface ProjectService {

    /**
     * 获得项目（只读）
     *
     * @param id 项目编号
     * @return 项目信息
     */
    ProjectDO getProject(Long id);

    /**
     * 获得项目分页列表（只读）
     *
     * @param pageReqVO 分页条件
     * @return 项目分页列表
     */
    PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO);

}
