package cn.iocoder.yudao.module.pms.project.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectAssignManagerReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectClassifyReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_SOURCE_KEY_DUPLICATE;

/**
 * PMS 项目 Service 实现类
 */
@Service
@Validated
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private CustomerMapper customerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(ProjectSaveReqVO createReqVO) {
        // 校验编码唯一
        validateCodeUnique(null, createReqVO.getCode());
        // 校验来源业务键唯一
        validateSourceKeyUnique(null, createReqVO.getSourceSystem(), createReqVO.getSourceBusinessKey());
        // 校验客户存在
        validateCustomerExists(createReqVO.getCustomerId());
        // 插入项目
        ProjectDO project = BeanUtils.toBean(createReqVO, ProjectDO.class);
        // 初始化为根项目（树字段在 insert 后回填，因为需要自增 id）
        project.setParentId(null);
        project.setDepth(0);
        project.setSort(0);
        projectMapper.insert(project);
        // 回填根项目树字段：root_id=自身 id，path=/{id}/
        ProjectDO treeUpdate = new ProjectDO();
        treeUpdate.setId(project.getId());
        treeUpdate.setRootId(project.getId());
        treeUpdate.setPath("/" + project.getId() + "/");
        projectMapper.updateById(treeUpdate);
        return project.getId();
    }

    @Override
    public void updateProject(ProjectSaveReqVO updateReqVO) {
        // 校验存在
        validateProjectExists(updateReqVO.getId());
        // 校验编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 校验来源业务键唯一
        validateSourceKeyUnique(updateReqVO.getId(), updateReqVO.getSourceSystem(), updateReqVO.getSourceBusinessKey());
        // 校验客户存在
        validateCustomerExists(updateReqVO.getCustomerId());
        // 更新项目（保留原有树字段，不通过通用 SaveReqVO 修改树结构）
        ProjectDO updateObj = BeanUtils.toBean(updateReqVO, ProjectDO.class);
        // 树字段需要走 ProjectTreeService，这里强制置空，避免被覆盖
        updateObj.setParentId(null);
        updateObj.setRootId(null);
        updateObj.setPath(null);
        updateObj.setDepth(null);
        updateObj.setSort(null);
        projectMapper.updateById(updateObj);
    }

    @Override
    public void deleteProject(Long id) {
        // 校验存在
        validateProjectExists(id);
        // 删除项目
        projectMapper.deleteById(id);
    }

    @Override
    public ProjectDO getProject(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO) {
        return projectMapper.selectPage(pageReqVO);
    }

    @Override
    public void classifyProject(ProjectClassifyReqVO reqVO) {
        // 校验存在
        validateProjectExists(reqVO.getProjectId());
        // 更新分类字段
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(reqVO.getProjectId());
        updateObj.setCategory(reqVO.getCategory());
        updateObj.setMajorProjectFlag(reqVO.getMajorProjectFlag());
        projectMapper.updateById(updateObj);
    }

    @Override
    public void assignProjectManager(ProjectAssignManagerReqVO reqVO) {
        // 校验存在
        validateProjectExists(reqVO.getProjectId());
        // 更新项目经理
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(reqVO.getProjectId());
        updateObj.setManagerUserId(reqVO.getManagerUserId());
        projectMapper.updateById(updateObj);
    }

    private void validateProjectExists(Long id) {
        if (id == null) {
            return;
        }
        if (projectMapper.selectById(id) == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        ProjectDO project = projectMapper.selectByCode(code);
        if (project == null) {
            return;
        }
        if (id == null || !project.getId().equals(id)) {
            throw exception(PROJECT_CODE_DUPLICATE);
        }
    }

    private void validateSourceKeyUnique(Long id, String sourceSystem, String sourceBusinessKey) {
        ProjectDO project = projectMapper.selectBySourceSystemAndBusinessKey(sourceSystem, sourceBusinessKey);
        if (project == null) {
            return;
        }
        if (id == null || !project.getId().equals(id)) {
            throw exception(PROJECT_SOURCE_KEY_DUPLICATE);
        }
    }

    private void validateCustomerExists(Long customerId) {
        if (customerId == null) {
            return;
        }
        CustomerDO customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw exception(PROJECT_CUSTOMER_NOT_EXISTS);
        }
    }

}
