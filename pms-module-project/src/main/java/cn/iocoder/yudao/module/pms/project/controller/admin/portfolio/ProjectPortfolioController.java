package cn.iocoder.yudao.module.pms.project.controller.admin.portfolio;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioMemberRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioRuleRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.portfolio.vo.ProjectPortfolioSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioMemberDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioRuleDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio.ProjectPortfolioRuleMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.service.portfolio.ProjectPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - PMS 项目组合")
@RestController
@RequestMapping("/pms/portfolio")
@Validated
public class ProjectPortfolioController {

    @Resource
    private ProjectPortfolioService portfolioService;
    @Resource
    private ProjectPortfolioRuleMapper ruleMapper;
    @Resource
    private ProjectMapper projectMapper;

    @PostMapping("/create")
    @Operation(summary = "创建项目组合")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:create')")
    public CommonResult<Long> createPortfolio(@Valid @RequestBody ProjectPortfolioSaveReqVO createReqVO) {
        return success(portfolioService.createPortfolio(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目组合")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:update')")
    public CommonResult<Boolean> updatePortfolio(@Valid @RequestBody ProjectPortfolioSaveReqVO updateReqVO) {
        portfolioService.updatePortfolio(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目组合")
    @Parameter(name = "id", description = "组合编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:delete')")
    public CommonResult<Boolean> deletePortfolio(@RequestParam("id") Long id) {
        portfolioService.deletePortfolio(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目组合")
    @Parameter(name = "id", description = "组合编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:query')")
    public CommonResult<ProjectPortfolioRespVO> getPortfolio(@RequestParam("id") Long id) {
        ProjectPortfolioDO portfolio = portfolioService.getPortfolio(id);
        ProjectPortfolioRespVO respVO = BeanUtils.toBean(portfolio, ProjectPortfolioRespVO.class);
        if (respVO != null) {
            // 填充规则列表
            List<ProjectPortfolioRuleDO> rules = ruleMapper.selectListByPortfolioId(id);
            respVO.setRules(BeanUtils.toBean(rules, ProjectPortfolioRuleRespVO.class));
            // 填充成员数量
            List<ProjectPortfolioMemberDO> members = portfolioService.getPortfolioMembers(id);
            respVO.setMemberCount(members.size());
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目组合分页")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:query')")
    public CommonResult<PageResult<ProjectPortfolioRespVO>> getPortfolioPage(@Validated ProjectPortfolioPageReqVO pageReqVO) {
        PageResult<ProjectPortfolioDO> pageResult = portfolioService.getPortfolioPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectPortfolioRespVO.class));
    }

    @PostMapping("/publish")
    @Operation(summary = "发布项目组合（计算成员、生成快照）")
    @Parameter(name = "id", description = "组合编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:publish')")
    public CommonResult<Boolean> publishPortfolio(@RequestParam("id") Long id) {
        portfolioService.publishPortfolio(id);
        return success(true);
    }

    @PostMapping("/recalculate")
    @Operation(summary = "重新计算动态成员")
    @Parameter(name = "id", description = "组合编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:update')")
    public CommonResult<Boolean> recalculateMembers(@RequestParam("id") Long id) {
        portfolioService.recalculateMembers(id);
        return success(true);
    }

    @GetMapping("/members")
    @Operation(summary = "获取组合成员列表")
    @Parameter(name = "portfolioId", description = "组合编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:portfolio:query')")
    public CommonResult<List<ProjectPortfolioMemberRespVO>> getPortfolioMembers(@RequestParam("portfolioId") Long portfolioId) {
        List<ProjectPortfolioMemberDO> members = portfolioService.getPortfolioMembers(portfolioId);
        // 批量查询项目信息
        Set<Long> projectIds = members.stream().map(ProjectPortfolioMemberDO::getProjectId)
                .collect(Collectors.toSet());
        Map<Long, ProjectDO> projectMap = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectByIds(projectIds).stream()
                .collect(Collectors.toMap(ProjectDO::getId, p -> p));
        // 组装返回 VO
        List<ProjectPortfolioMemberRespVO> result = new ArrayList<>();
        for (ProjectPortfolioMemberDO member : members) {
            ProjectPortfolioMemberRespVO respVO = BeanUtils.toBean(member, ProjectPortfolioMemberRespVO.class);
            ProjectDO project = projectMap.get(member.getProjectId());
            if (project != null) {
                respVO.setProjectCode(project.getCode());
                respVO.setProjectName(project.getName());
            }
            result.add(respVO);
        }
        return success(result);
    }

}
