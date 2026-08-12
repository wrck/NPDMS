package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeCreateChildReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttree.vo.ProjectTreeNodeRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customer.CustomerMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
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
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PARENT_ERROR;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PARENT_NOT_EXISTS;

/**
 * PMS 项目树 Service 实现类
 *
 * 物化路径模型：path 格式 /{rootId}/.../{selfId}/，depth 从 0 开始。
 */
@Service
@Validated
public class ProjectTreeServiceImpl implements ProjectTreeService {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private CustomerMapper customerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChildProject(ProjectTreeCreateChildReqVO reqVO) {
        // 校验父项目存在
        ProjectDO parent = projectMapper.selectById(reqVO.getParentId());
        if (parent == null) {
            throw exception(PROJECT_TREE_PARENT_NOT_EXISTS);
        }
        // 校验编码唯一
        validateCodeUnique(null, reqVO.getCode());
        // 校验客户存在
        validateCustomerExists(reqVO.getCustomerId());
        // 构建子项目
        ProjectDO child = new ProjectDO();
        child.setCode(reqVO.getCode());
        child.setName(reqVO.getName());
        child.setCustomerId(reqVO.getCustomerId());
        child.setStatus(0);
        child.setSourceSystem("PMS");
        child.setSourceBusinessKey("PMS-" + reqVO.getCode());
        child.setCategory(reqVO.getCategory());
        child.setMajorProjectFlag(Boolean.TRUE.equals(reqVO.getMajorProjectFlag()));
        child.setManagerUserId(reqVO.getManagerUserId());
        // 树字段：先填父相关，path 待 insert 后回填
        child.setParentId(parent.getId());
        child.setRootId(parent.getRootId() != null ? parent.getRootId() : parent.getId());
        child.setDepth(parent.getDepth() != null ? parent.getDepth() + 1 : 1);
        child.setSort(reqVO.getSort() != null ? reqVO.getSort() : 0);
        projectMapper.insert(child);
        // 回填 path：父 path + self id + /
        String path = (parent.getPath() != null ? parent.getPath() : "/" + parent.getId() + "/")
                + child.getId() + "/";
        ProjectDO pathUpdate = new ProjectDO();
        pathUpdate.setId(child.getId());
        pathUpdate.setPath(path);
        projectMapper.updateById(pathUpdate);
        return child.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveSubtree(ProjectTreeMoveReqVO reqVO) {
        // 校验待移动项目存在
        ProjectDO project = projectMapper.selectById(reqVO.getProjectId());
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        // 计算新的父项目、根、深度
        ProjectDO newParent = null;
        if (reqVO.getTargetParentId() != null && reqVO.getTargetParentId() > 0) {
            newParent = projectMapper.selectById(reqVO.getTargetParentId());
            if (newParent == null) {
                throw exception(PROJECT_TREE_PARENT_NOT_EXISTS);
            }
            // 校验目标父项目不是待移动项目自身或其子孙（防止环）
            if (newParent.getId().equals(project.getId())
                    || (newParent.getPath() != null && project.getPath() != null
                        && newParent.getPath().startsWith(project.getPath()))) {
                throw exception(PROJECT_TREE_PARENT_ERROR);
            }
        }
        // 计算新旧路径
        String oldPath = project.getPath();
        Long newRootId;
        Integer newDepth;
        String newParentPath;
        if (newParent == null) {
            // 移到根级：成为新的根项目
            newRootId = project.getId();
            newDepth = 0;
            newParentPath = "/";
        } else {
            newRootId = newParent.getRootId() != null ? newParent.getRootId() : newParent.getId();
            newDepth = (newParent.getDepth() != null ? newParent.getDepth() : 0) + 1;
            newParentPath = newParent.getPath() != null ? newParent.getPath() : "/" + newParent.getId() + "/";
        }
        String newPath = newParentPath + project.getId() + "/";
        // 更新当前项目
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(project.getId());
        updateObj.setParentId(newParent != null ? newParent.getId() : null);
        updateObj.setRootId(newRootId);
        updateObj.setDepth(newDepth);
        updateObj.setPath(newPath);
        projectMapper.updateById(updateObj);
        // 更新所有后代：路径前缀替换，深度按差值调整，rootId 统一更新
        if (oldPath != null && !oldPath.equals(newPath)) {
            List<ProjectDO> descendants = projectMapper.selectListByPathPrefix(oldPath);
            int depthDelta = newDepth - (project.getDepth() != null ? project.getDepth() : 0);
            for (ProjectDO desc : descendants) {
                if (desc.getId().equals(project.getId())) {
                    continue;
                }
                String descNewPath = newPath + desc.getPath().substring(oldPath.length());
                ProjectDO descUpdate = new ProjectDO();
                descUpdate.setId(desc.getId());
                descUpdate.setRootId(newRootId);
                descUpdate.setPath(descNewPath);
                descUpdate.setDepth((desc.getDepth() != null ? desc.getDepth() : 0) + depthDelta);
                projectMapper.updateById(descUpdate);
            }
        }
    }

    @Override
    public ProjectTreeNodeRespVO getProjectTree(Long rootProjectId) {
        ProjectDO root = projectMapper.selectById(rootProjectId);
        if (root == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        // 查询所有同根项目
        List<ProjectDO> all = projectMapper.selectListByRootId(
                root.getRootId() != null ? root.getRootId() : root.getId());
        // 构建 id -> node 映射
        Map<Long, ProjectTreeNodeRespVO> nodeMap = new LinkedHashMap<>();
        for (ProjectDO p : all) {
            nodeMap.put(p.getId(), BeanUtils.toBean(p, ProjectTreeNodeRespVO.class));
        }
        // 构建树
        ProjectTreeNodeRespVO rootNode = null;
        for (ProjectDO p : all) {
            ProjectTreeNodeRespVO node = nodeMap.get(p.getId());
            if (p.getParentId() == null) {
                rootNode = node;
            } else {
                ProjectTreeNodeRespVO parentNode = nodeMap.get(p.getParentId());
                if (parentNode != null) {
                    if (parentNode.getChildren() == null) {
                        parentNode.setChildren(new ArrayList<>());
                    }
                    parentNode.getChildren().add(node);
                }
            }
        }
        // 排序子节点
        sortTree(rootNode);
        return rootNode;
    }

    @Override
    public List<ProjectDO> getDescendants(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null || project.getPath() == null) {
            return new ArrayList<>();
        }
        List<ProjectDO> list = projectMapper.selectListByPathPrefix(project.getPath());
        list.removeIf(p -> p.getId().equals(projectId));
        list.sort(Comparator.comparing(ProjectDO::getPath, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    @Override
    public List<ProjectDO> getProjectPath(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null || project.getPath() == null) {
            return new ArrayList<>();
        }
        // 解析 path 中的 id 列表
        String[] segments = project.getPath().split("/");
        List<Long> pathIds = new ArrayList<>();
        for (String seg : segments) {
            if (!seg.isEmpty()) {
                pathIds.add(Long.valueOf(seg));
            }
        }
        if (pathIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProjectDO> pathProjects = projectMapper.selectByIds(pathIds);
        // 按路径顺序排序
        Map<Long, ProjectDO> projectMap = new LinkedHashMap<>();
        for (ProjectDO p : pathProjects) {
            projectMap.put(p.getId(), p);
        }
        List<ProjectDO> result = new ArrayList<>();
        for (Long id : pathIds) {
            ProjectDO p = projectMap.get(id);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    private void sortTree(ProjectTreeNodeRespVO node) {
        if (node == null || node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        node.getChildren().sort(Comparator.comparing(
                ProjectTreeNodeRespVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectTreeNodeRespVO::getId));
        for (ProjectTreeNodeRespVO child : node.getChildren()) {
            sortTree(child);
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
