package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.Decision;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.IdempotencyScope;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.SuccessFacts;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ORGANIZATION_SCOPE_INVALID;

/** F-PROJ-001正式创建的唯一应用事务入口。 */
@Service
public class ProjectManualCreationApplicationService {

    public static final String CREATE_SCOPE = "POST:/pms/projects";

    @Resource
    private ProjectCreationPlatformFactService platformFactService;
    @Resource
    private ProjectManualCreationService projectCreationService;
    @Resource
    private ProjectCreationAuthorizationService authorizationService;
    @Resource
    private CompanyApi companyApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private OrganizationScopeApi organizationScopeApi;
    @Resource
    private ProjectSiteApplicationService projectSiteService;

    public ManualProjectCreateResult create(ManualProjectCreateCommand command, Actor actor) {
        validate(command, actor);
        authorizationService.assertCanCreate(actor.actorId());
        var execution = platformFactService.execute(
                new IdempotencyScope(actor.tenantId(), CREATE_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), ManualProjectCreateResult.class,
                () -> createOnce(command, actor.tenantId()),
                result -> successFacts(command, actor, result));
        if (execution.decision() == Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        return execution.response();
    }

    private ManualProjectCreateResult createOnce(ManualProjectCreateCommand command, Long tenantId) {
        CompanyRespDTO company = resolveCompany(command.orderOfficeCompanyId());
        DeptRespDTO department = resolveDepartment(command.orderOfficeDepartmentId());
        command.draft().setTenantId(tenantId);
        command.draft().setCompanyId(company.getId());
        command.draft().setCompanyCode(company.getCode());
        command.draft().setCompanyName(company.getName());
        command.draft().setDepartmentId(department.getId());
        command.draft().setDepartmentCode(department.getCode());
        command.draft().setDepartmentName(department.getName());
        command.draft().setLocationResolutionStatus(projectSiteService.validateLocationScope(
                command.sites(), command.draft().getImplementationLocation()));
        ProjectMasterDO project = projectCreationService.createProject(command.draft(),
                company.getCode(), department.getCode(), command.templateRevisionId(),
                command.candidateWatermark(), null);
        projectSiteService.bindSites(project.getId(), command.sites());
        ProjectInstantiation instances = projectCreationService.getInstances(project.getId());
        return new ManualProjectCreateResult(
                project.getId(), project.getProjectCode(), project.getStatus(), project.getLifecycleStatus(),
                project.getCurrentStage(), project.getAssignmentStatus(), project.getVersion(),
                project.getLifecycleTemplateId(), project.getLifecycleTemplateRevisionNo(),
                project.getTemplateLoadMethod(), instances.getStages().size(), instances.getTasks().size(),
                instances.getMilestones().size(), instances.getDeliverables().size(), instances.getGates().size(),
                false);
    }

    private SuccessFacts successFacts(ManualProjectCreateCommand command, Actor actor,
                                      ManualProjectCreateResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", result.id());
        detail.put("templateId", result.lifecycleTemplateId());
        detail.put("templateRevisionNo", result.lifecycleTemplateRevisionNo());
        detail.put("creationReasonDigest", sha256(command.draft().getCreationReason()));
        detail.put("stageCount", result.stageCount());
        detail.put("taskCount", result.taskCount());
        detail.put("milestoneCount", result.milestoneCount());
        detail.put("deliverableCount", result.deliverableCount());
        detail.put("gateCount", result.gateCount());
        return new SuccessFacts("PROJECT_CREATE", "Project", String.valueOf(result.id()),
                actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectCreated", JsonUtils.toJsonString(result));
    }

    private void validate(ManualProjectCreateCommand command, Actor actor) {
        if (command == null || command.draft() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.requestDigest() == null
                || command.orderOfficeCompanyId() == null || command.orderOfficeDepartmentId() == null
                || command.draft().getParentId() == null
                    && (command.candidateWatermark() == null || command.candidateWatermark().isBlank())
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("正式项目创建命令不完整");
        }
        companyApi.validateCompanyList(List.of(command.orderOfficeCompanyId()));
        deptApi.validateDeptList(List.of(command.orderOfficeDepartmentId()));
        if (!organizationScopeApi.hasScope(actor.actorId(), command.orderOfficeCompanyId(),
                command.orderOfficeDepartmentId())) {
            throw exception(PROJECT_ORGANIZATION_SCOPE_INVALID, "当前操作人无下单公司与办事处的联合范围");
        }
    }

    private CompanyRespDTO resolveCompany(Long id) {
        CompanyRespDTO company = companyApi.getCompany(id);
        if (company == null) {
            throw exception(PROJECT_ORGANIZATION_SCOPE_INVALID, "公司不存在或已停用");
        }
        return company;
    }

    private DeptRespDTO resolveDepartment(Long id) {
        DeptRespDTO department = deptApi.getDept(id);
        if (department == null) {
            throw exception(PROJECT_ORGANIZATION_SCOPE_INVALID, "部门不存在或已停用");
        }
        return department;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
