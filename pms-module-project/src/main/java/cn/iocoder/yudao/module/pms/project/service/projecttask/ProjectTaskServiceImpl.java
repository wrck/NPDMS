package cn.iocoder.yudao.module.pms.project.service.projecttask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttask.vo.ProjectTaskTreeRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttask.ProjectTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttask.ProjectTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_HAS_CHILDREN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_PARENT_ERROR;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_PARENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_PROJECT_NOT_EXISTS;

/**
 * PMS 项目任务 WBS Service 实现类
 */
@Service
@Validated
public class ProjectTaskServiceImpl implements ProjectTaskService {

    @Resource
    private ProjectTaskMapper projectTaskMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectTask(ProjectTaskSaveReqVO createReqVO) {
        // 校验项目存在
        validateProjectExists(createReqVO.getProjectId());
        // 校验父任务存在（如果指定）
        ProjectTaskDO parent = null;
        if (createReqVO.getParentId() != null) {
            parent = projectTaskMapper.selectById(createReqVO.getParentId());
            if (parent == null) {
                throw exception(PROJECT_TASK_PARENT_NOT_EXISTS);
            }
            // 校验父子任务属于同一项目
            if (!parent.getProjectId().equals(createReqVO.getProjectId())) {
                throw exception(PROJECT_TASK_PARENT_ERROR);
            }
        }
        // 校验任务编码在项目内唯一（仅在 code 非空时校验）
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入任务
        ProjectTaskDO task = BeanUtils.toBean(createReqVO, ProjectTaskDO.class);
        if (task.getStatus() == null) {
            task.setStatus(0);
        }
        if (task.getProgress() == null) {
            task.setProgress(0);
        }
        if (task.getSort() == null) {
            task.setSort(0);
        }
        // 树字段：先填父相关，path 待 insert 后回填
        if (parent != null) {
            task.setParentId(parent.getId());
            task.setRootId(parent.getRootId() != null ? parent.getRootId() : parent.getId());
            task.setDepth(parent.getDepth() != null ? parent.getDepth() + 1 : 1);
        } else {
            task.setParentId(null);
            task.setDepth(0);
        }
        projectTaskMapper.insert(task);
        // 回填 rootId（根任务）和 path
        Long rootId = task.getRootId() != null ? task.getRootId() : task.getId();
        String newPath;
        if (parent != null && parent.getPath() != null) {
            newPath = parent.getPath() + task.getId() + "/";
        } else {
            newPath = "/" + task.getId() + "/";
        }
        ProjectTaskDO treeUpdate = new ProjectTaskDO();
        treeUpdate.setId(task.getId());
        treeUpdate.setRootId(rootId);
        treeUpdate.setPath(newPath);
        projectTaskMapper.updateById(treeUpdate);
        return task.getId();
    }

    @Override
    public void updateProjectTask(ProjectTaskSaveReqVO updateReqVO) {
        // 校验存在
        ProjectTaskDO existing = validateTaskExists(updateReqVO.getId());
        // 校验项目存在
        validateProjectExists(updateReqVO.getProjectId());
        // 校验父任务一致（不允许通过 update 改父，必须走 move 接口）
        if (updateReqVO.getParentId() != null && !updateReqVO.getParentId().equals(existing.getParentId())) {
            throw exception(PROJECT_TASK_PARENT_ERROR);
        }
        // 校验任务编码在项目内唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 更新任务（保留树字段）
        ProjectTaskDO updateObj = BeanUtils.toBean(updateReqVO, ProjectTaskDO.class);
        updateObj.setParentId(null);
        updateObj.setRootId(null);
        updateObj.setPath(null);
        updateObj.setDepth(null);
        projectTaskMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectTask(Long id) {
        // 校验存在
        validateTaskExists(id);
        // 校验是否存在子任务
        List<ProjectTaskDO> children = projectTaskMapper.selectListByParentId(id);
        if (children != null && !children.isEmpty()) {
            throw exception(PROJECT_TASK_HAS_CHILDREN);
        }
        // 删除任务
        projectTaskMapper.deleteById(id);
    }

    @Override
    public ProjectTaskDO getProjectTask(Long id) {
        return projectTaskMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectTaskDO> getProjectTaskPage(ProjectTaskPageReqVO pageReqVO) {
        return projectTaskMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ProjectTaskTreeRespVO> getProjectTaskTree(Long projectId) {
        // 查询项目下所有任务
        List<ProjectTaskDO> all = projectTaskMapper.selectListByProjectId(projectId);
        // 构建 id -> node 映射
        Map<Long, ProjectTaskTreeRespVO> nodeMap = new LinkedHashMap<>();
        for (ProjectTaskDO t : all) {
            nodeMap.put(t.getId(), BeanUtils.toBean(t, ProjectTaskTreeRespVO.class));
        }
        // 构建森林
        List<ProjectTaskTreeRespVO> roots = new ArrayList<>();
        for (ProjectTaskDO t : all) {
            ProjectTaskTreeRespVO node = nodeMap.get(t.getId());
            if (t.getParentId() == null) {
                roots.add(node);
            } else {
                ProjectTaskTreeRespVO parentNode = nodeMap.get(t.getParentId());
                if (parentNode != null) {
                    if (parentNode.getChildren() == null) {
                        parentNode.setChildren(new ArrayList<>());
                    }
                    parentNode.getChildren().add(node);
                } else {
                    // 父节点不在列表内，作为根节点处理
                    roots.add(node);
                }
            }
        }
        // 排序
        sortForest(roots);
        return roots;
    }

    @Override
    public List<ProjectTaskDO> getProjectTaskDescendants(Long taskId) {
        ProjectTaskDO task = projectTaskMapper.selectById(taskId);
        if (task == null || task.getPath() == null) {
            return new ArrayList<>();
        }
        List<ProjectTaskDO> list = projectTaskMapper.selectListByPathPrefix(task.getPath());
        list.removeIf(p -> p.getId().equals(taskId));
        list.sort(Comparator.comparing(ProjectTaskDO::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveProjectTask(ProjectTaskMoveReqVO reqVO) {
        // 校验待移动任务存在
        ProjectTaskDO task = projectTaskMapper.selectById(reqVO.getTaskId());
        if (task == null) {
            throw exception(PROJECT_TASK_NOT_EXISTS);
        }
        // 计算新的父任务、根、深度
        ProjectTaskDO newParent = null;
        if (reqVO.getTargetParentId() != null && reqVO.getTargetParentId() > 0) {
            newParent = projectTaskMapper.selectById(reqVO.getTargetParentId());
            if (newParent == null) {
                throw exception(PROJECT_TASK_PARENT_NOT_EXISTS);
            }
            // 校验父子任务属于同一项目
            if (!newParent.getProjectId().equals(task.getProjectId())) {
                throw exception(PROJECT_TASK_PARENT_ERROR);
            }
            // 校验目标父任务不是待移动任务自身或其子孙（防止环）
            if (newParent.getId().equals(task.getId())
                    || (newParent.getPath() != null && task.getPath() != null
                        && newParent.getPath().startsWith(task.getPath()))) {
                throw exception(PROJECT_TASK_PARENT_ERROR);
            }
        }
        // 计算新旧路径
        String oldPath = task.getPath();
        Long newRootId;
        Integer newDepth;
        String newParentPath;
        if (newParent == null) {
            newRootId = task.getId();
            newDepth = 0;
            newParentPath = "/";
        } else {
            newRootId = newParent.getRootId() != null ? newParent.getRootId() : newParent.getId();
            newDepth = (newParent.getDepth() != null ? newParent.getDepth() : 0) + 1;
            newParentPath = newParent.getPath() != null ? newParent.getPath() : "/" + newParent.getId() + "/";
        }
        String newPath = newParentPath + task.getId() + "/";
        // 更新当前任务
        ProjectTaskDO updateObj = new ProjectTaskDO();
        updateObj.setId(task.getId());
        updateObj.setParentId(newParent != null ? newParent.getId() : null);
        updateObj.setRootId(newRootId);
        updateObj.setDepth(newDepth);
        updateObj.setPath(newPath);
        projectTaskMapper.updateById(updateObj);
        // 更新所有后代
        if (oldPath != null && !oldPath.equals(newPath)) {
            List<ProjectTaskDO> descendants = projectTaskMapper.selectListByPathPrefix(oldPath);
            int depthDelta = newDepth - (task.getDepth() != null ? task.getDepth() : 0);
            for (ProjectTaskDO desc : descendants) {
                if (desc.getId().equals(task.getId())) {
                    continue;
                }
                String descNewPath = newPath + desc.getPath().substring(oldPath.length());
                ProjectTaskDO descUpdate = new ProjectTaskDO();
                descUpdate.setId(desc.getId());
                descUpdate.setRootId(newRootId);
                descUpdate.setPath(descNewPath);
                descUpdate.setDepth((desc.getDepth() != null ? desc.getDepth() : 0) + depthDelta);
                projectTaskMapper.updateById(descUpdate);
            }
        }
    }

    private void sortForest(List<ProjectTaskTreeRespVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(
                ProjectTaskTreeRespVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectTaskTreeRespVO::getId));
        for (ProjectTaskTreeRespVO node : nodes) {
            sortForest(node.getChildren());
        }
    }

    private ProjectTaskDO validateTaskExists(Long id) {
        if (id == null) {
            return null;
        }
        ProjectTaskDO task = projectTaskMapper.selectById(id);
        if (task == null) {
            throw exception(PROJECT_TASK_NOT_EXISTS);
        }
        return task;
    }

    private void validateProjectExists(Long projectId) {
        if (projectId == null) {
            return;
        }
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw exception(PROJECT_TASK_PROJECT_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        ProjectTaskDO existing = projectTaskMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !existing.getId().equals(id)) {
            throw exception(PROJECT_TASK_CODE_DUPLICATE);
        }
    }

}
