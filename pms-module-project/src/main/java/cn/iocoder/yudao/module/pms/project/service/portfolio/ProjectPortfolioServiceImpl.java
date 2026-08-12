package cn.iocoder.yudao.module.pms.project.service.portfolio;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioRuleDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio.ProjectPortfolioMemberMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio.ProjectPortfolioMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio.ProjectPortfolioRuleMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PORTFOLIO_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PORTFOLIO_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PORTFOLIO_PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PORTFOLIO_STATUS_INVALID;

/**
 * PMS 项目组合 Service 实现类
 */
@Service
@Validated
public class ProjectPortfolioServiceImpl implements ProjectPortfolioService {

    @Resource
    private ProjectPortfolioMapper portfolioMapper;
    @Resource
    private ProjectPortfolioMemberMapper memberMapper;
    @Resource
    private ProjectPortfolioRuleMapper ruleMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPortfolio(ProjectPortfolioSaveReqVO createReqVO) {
        // 校验编码唯一
        validateCodeUnique(null, createReqVO.getCode());
        // 插入组合
        ProjectPortfolioDO portfolio = BeanUtils.toBean(createReqVO, ProjectPortfolioDO.class);
        portfolioMapper.insert(portfolio);
        Long portfolioId = portfolio.getId();
        // 保存子表
        saveChildren(portfolioId, createReqVO);
        return portfolioId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePortfolio(ProjectPortfolioSaveReqVO updateReqVO) {
        // 校验存在
        ProjectPortfolioDO existing = validatePortfolioExists(updateReqVO.getId());
        // 已发布的组合不允许修改成员配置
        // 校验编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        // 更新组合
        ProjectPortfolioDO updateObj = BeanUtils.toBean(updateReqVO, ProjectPortfolioDO.class);
        portfolioMapper.updateById(updateObj);
        // 仅草稿状态允许重建成员与规则
        if (existing.getStatus() != null && existing.getStatus() == 0) {
            saveChildren(updateReqVO.getId(), updateReqVO);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePortfolio(Long id) {
        // 校验存在
        validatePortfolioExists(id);
        // 删除子表与主表
        memberMapper.deleteByPortfolioId(id);
        ruleMapper.deleteByPortfolioId(id);
        portfolioMapper.deleteById(id);
    }

    @Override
    public ProjectPortfolioDO getPortfolio(Long id) {
        return portfolioMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectPortfolioDO> getPortfolioPage(ProjectPortfolioPageReqVO pageReqVO) {
        return portfolioMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishPortfolio(Long id) {
        // 校验存在
        ProjectPortfolioDO portfolio = validatePortfolioExists(id);
        // 仅草稿状态可发布
        if (portfolio.getStatus() == null || portfolio.getStatus() != 0) {
            throw exception(PORTFOLIO_STATUS_INVALID);
        }
        // 动态组合：计算成员
        if ("DYNAMIC".equals(portfolio.getMemberType())) {
            recalculateDynamicMembers(id);
        }
        // 更新状态为已发布
        ProjectPortfolioDO updateObj = new ProjectPortfolioDO();
        updateObj.setId(id);
        updateObj.setStatus(1);
        portfolioMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateMembers(Long id) {
        // 校验存在
        ProjectPortfolioDO portfolio = validatePortfolioExists(id);
        // 仅动态组合可重算
        if (!"DYNAMIC".equals(portfolio.getMemberType())) {
            throw exception(PORTFOLIO_STATUS_INVALID);
        }
        recalculateDynamicMembers(id);
    }

    @Override
    public List<ProjectPortfolioMemberDO> getPortfolioMembers(Long portfolioId) {
        return memberMapper.selectListByPortfolioId(portfolioId);
    }

    // ========== 私有方法 ==========

    private void saveChildren(Long portfolioId, ProjectPortfolioSaveReqVO reqVO) {
        // 清理旧规则与旧成员
        ruleMapper.deleteByPortfolioId(portfolioId);
        memberMapper.deleteByPortfolioId(portfolioId);
        if ("DYNAMIC".equals(reqVO.getMemberType())) {
            // 保存动态规则
            if (reqVO.getRules() != null) {
                for (ProjectPortfolioRuleSaveReqVO ruleVO : reqVO.getRules()) {
                    ProjectPortfolioRuleDO rule = BeanUtils.toBean(ruleVO, ProjectPortfolioRuleDO.class);
                    rule.setId(null);
                    rule.setPortfolioId(portfolioId);
                    ruleMapper.insert(rule);
                }
            }
        } else if ("STATIC".equals(reqVO.getMemberType())) {
            // 保存静态成员
            if (reqVO.getStaticProjectIds() != null) {
                for (Long projectId : reqVO.getStaticProjectIds()) {
                    // 校验项目存在
                    if (projectMapper.selectById(projectId) == null) {
                        throw exception(PORTFOLIO_PROJECT_NOT_EXISTS);
                    }
                    ProjectPortfolioMemberDO member = new ProjectPortfolioMemberDO();
                    member.setPortfolioId(portfolioId);
                    member.setProjectId(projectId);
                    member.setInclusionType("STATIC");
                    member.setInclusionReason("手动选择");
                    member.setStatus(1);
                    memberMapper.insert(member);
                }
            }
        }
    }

    /**
     * 重新计算动态成员：根据规则查询匹配项目，重建动态成员记录
     */
    private void recalculateDynamicMembers(Long portfolioId) {
        List<ProjectPortfolioRuleDO> rules = ruleMapper.selectListByPortfolioId(portfolioId);
        // 删除旧的动态成员（保留静态成员）
        List<ProjectPortfolioMemberDO> existingMembers = memberMapper.selectListByPortfolioId(portfolioId);
        for (ProjectPortfolioMemberDO member : existingMembers) {
            if ("DYNAMIC".equals(member.getInclusionType())) {
                memberMapper.deleteById(member.getId());
            }
        }
        // 根据规则查询匹配项目
        List<ProjectDO> matchedProjects;
        if (rules.isEmpty()) {
            matchedProjects = projectMapper.selectList(new QueryWrapper<>());
        } else {
            QueryWrapper<ProjectDO> wrapper = new QueryWrapper<>();
            for (ProjectPortfolioRuleDO rule : rules) {
                applyRule(wrapper, rule);
            }
            matchedProjects = projectMapper.selectList(wrapper);
        }
        // 生成动态成员记录
        String reason = buildInclusionReason(rules);
        for (ProjectDO project : matchedProjects) {
            ProjectPortfolioMemberDO member = new ProjectPortfolioMemberDO();
            member.setPortfolioId(portfolioId);
            member.setProjectId(project.getId());
            member.setInclusionType("DYNAMIC");
            member.setInclusionReason(reason);
            member.setStatus(1);
            memberMapper.insert(member);
        }
    }

    /**
     * 将单条规则应用到查询条件（AND 逻辑）
     */
    private void applyRule(QueryWrapper<ProjectDO> wrapper, ProjectPortfolioRuleDO rule) {
        String column = mapRuleColumn(rule.getRuleField());
        String operator = rule.getRuleOperator();
        String value = rule.getRuleValue();
        switch (operator) {
            case "EQ":
                wrapper.eq(column, value);
                break;
            case "NE":
                wrapper.ne(column, value);
                break;
            case "IN":
                wrapper.in(column, Arrays.asList(value.split(",")));
                break;
            case "LIKE":
                wrapper.like(column, value);
                break;
            default:
                wrapper.eq(column, value);
        }
    }

    /**
     * 规则字段映射为数据库列名
     */
    private String mapRuleColumn(String ruleField) {
        switch (ruleField) {
            case "CUSTOMER":
                return "customer_id";
            case "REGION":
                // ProjectDO 无 region 字段，REGION 规则映射到 industry（行业）
                return "industry";
            case "TYPE":
                return "project_type";
            case "STATUS":
                return "status";
            default:
                return ruleField.toLowerCase();
        }
    }

    /**
     * 构建纳入原因描述
     */
    private String buildInclusionReason(List<ProjectPortfolioRuleDO> rules) {
        if (rules.isEmpty()) {
            return "无规则，纳入全部项目";
        }
        StringBuilder sb = new StringBuilder("匹配规则：");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            ProjectPortfolioRuleDO rule = rules.get(i);
            sb.append(rule.getRuleField()).append(" ").append(rule.getRuleOperator())
                    .append(" ").append(rule.getRuleValue());
        }
        return sb.toString();
    }

    private ProjectPortfolioDO validatePortfolioExists(Long id) {
        if (id == null) {
            throw exception(PORTFOLIO_NOT_EXISTS);
        }
        ProjectPortfolioDO portfolio = portfolioMapper.selectById(id);
        if (portfolio == null) {
            throw exception(PORTFOLIO_NOT_EXISTS);
        }
        return portfolio;
    }

    private void validateCodeUnique(Long id, String code) {
        ProjectPortfolioDO portfolio = portfolioMapper.selectByCode(code);
        if (portfolio == null) {
            return;
        }
        if (id == null || !portfolio.getId().equals(id)) {
            throw exception(PORTFOLIO_CODE_DUPLICATE);
        }
    }

}
