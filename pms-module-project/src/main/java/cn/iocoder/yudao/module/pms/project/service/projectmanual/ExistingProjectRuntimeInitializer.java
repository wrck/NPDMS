package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskTreePathMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.DeliverableDefinition;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;


/** Existing unbound projects reuse the creation instantiation sequence without changing their identity or assignments. */
@Service
public class ExistingProjectRuntimeInitializer {
    @Resource
    private cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer runtimeGraphFreezer;
    @Resource
    private cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectPlanInitializationService planInitializationService;
    @Resource
    private ProjectStageInstanceMapper stageInstanceMapper;
    @Resource
    private ProjectTaskInstanceMapper taskInstanceMapper;
    @Resource
    private ProjectTaskExecutionContractMapper taskExecutionContractMapper;
    @Resource
    private SatisfactionQuestionnaireTemplateApi satisfactionQuestionnaireTemplateApi;
    @Resource
    private ProjectTaskTreePathMapper taskTreePathMapper;
    @Resource
    private ProjectMilestoneInstanceMapper milestoneInstanceMapper;
    @Resource
    private ProjectGateInstanceMapper gateInstanceMapper;
    @Resource
    private ProjectGateReferenceInstanceMapper gateReferenceInstanceMapper;
    @Resource
    private TaskExecutionContractFactory taskExecutionContractFactory;
    @Resource
    private ProjectDeliverableInitializationApplicationService deliverableInitializationApplicationService;

    public record Initialize(ProjectMasterDO project, ProjectTemplateRevisionDO revision,
                             cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot snapshot,
                             Long stateMachineRevisionId, LocalDateTime initializedAt) { }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, rollbackFor = Exception.class)
    public void initialize(Initialize command) {
        var draft = command.project();
        var content = command.snapshot().toRuntimeContent();
        Long trustedTenantId = draft.getTenantId();
        var instantiationTime = command.initializedAt();
        TemplateInstantiator.requireStages(content);
        runtimeGraphFreezer.validate(content);
        // f) 冻结版本实例化五要素 + 门禁引用行（source_definition_id 无定义行ID时保持 NULL）
        ProjectInstantiation instantiation = TemplateInstantiator.instantiate(
                content, draft.getId(), command.stateMachineRevisionId(), IdWorker::getId);
        instantiation.getStages().forEach(stage -> {
            stage.setTenantId(trustedTenantId);
            stageInstanceMapper.insert(stage);
        });
        var stageContracts = runtimeGraphFreezer.freeze(trustedTenantId, draft.getId(), command.revision().getId(), content,
                instantiation.getStages(), instantiationTime);
        freezeSatisfactionFacts(draft, instantiation);
        // 任务ID已在落库前确定；先写完整任务集合，再写闭包和一任务一当前执行契约。
        instantiation.getTasks().forEach(taskInstanceMapper::insert);
        insertIfNotEmpty(instantiation.getTaskTreePaths(), taskTreePathMapper::insertBatch);
        Map<String, TemplateDefinitionContent.TaskDef> definitionsByCode = new LinkedHashMap<>();
        content.getTasks().stream().filter(Objects::nonNull)
                .forEach(definition -> definitionsByCode.put(definition.getTaskCode(), definition));
        List<ProjectTaskExecutionContractDO> taskContracts = new ArrayList<>();
        for (var task : instantiation.getTasks()) {
            TemplateDefinitionContent.TaskDef definition = definitionsByCode.get(task.getCode());
            if (definition == null) {
                throw new IllegalArgumentException("模板任务定义不存在：" + task.getCode());
            }
            ProjectTaskExecutionContractDO contract = taskExecutionContractFactory.create(
                    task.getId(), definition.getId(), definition, instantiationTime);
            contract.setTenantId(draft.getTenantId());
            taskExecutionContractMapper.insert(contract);
            taskContracts.add(contract);
        }
        insertIfNotEmpty(instantiation.getMilestones(), milestoneInstanceMapper::insertBatch);
        List<DeliverableDefinition> deliverableDefinitions = content.getDeliverables().stream()
                .map(definition -> new DeliverableDefinition(
                        definition.getDeliverableCode(), definition.getName(), definition.getStageCode(),
                        definition.getTaskCode(), Boolean.TRUE.equals(definition.getRequired()), definition.getId()))
                .toList();
        deliverableInitializationApplicationService.initialize(new InitializeProjectDeliverablesCommand(
                draft.getId(), command.revision().getId(), deliverableDefinitions));
        // 门禁需先落库取自增 id，供引用行回填 gate_id
        instantiation.getGates().forEach(gateInstanceMapper::insert);
        for (ProjectGateInstanceDO gate : instantiation.getGates()) {
            List<ProjectGateReferenceInstanceDO> references =
                    instantiation.getGateReferencesByGateCode().get(gate.getGateCode());
            if (references == null) {
                continue;
            }
            for (ProjectGateReferenceInstanceDO reference : references) {
                reference.setGateId(gate.getId());
                gateReferenceInstanceMapper.insert(reference);
            }
        }

        planInitializationService.initialize(draft, command.snapshot(), stageContracts, taskContracts);
    }

    private <T> void insertIfNotEmpty(List<T> entities, Consumer<List<T>> inserter) {
        if (entities != null && !entities.isEmpty()) {
            inserter.accept(entities);
        }
    }

    private void freezeSatisfactionFacts(ProjectMasterDO project, ProjectInstantiation instantiation) {
        for (var task : instantiation.getTasks()) {
            ProjectTaskSatisfactionSnapshot.freeze(project,task,satisfactionQuestionnaireTemplateApi);
        }
    }

}
