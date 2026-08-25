package cn.iocoder.yudao.module.pms.project.service.acceptance.application;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** ACC交付件初始化；必须参与PROJ已开启的同库事务。 */
@Service
public class ProjectDeliverableInitializationApplicationServiceImpl
        implements ProjectDeliverableInitializationApplicationService {

    @Resource
    private AccProjectDeliverableMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DeliverableInitializationResult initialize(InitializeProjectDeliverablesCommand command) {
        if (command == null || command.projectId() == null || command.templateRevisionId() == null
                || command.definitions() == null) {
            throw new IllegalArgumentException("交付件初始化命令不完整");
        }
        List<AccProjectDeliverableDO> rows = command.definitions().stream()
                .map(definition -> toDataObject(command.projectId(), definition))
                .toList();
        if (!rows.isEmpty() && !Boolean.TRUE.equals(mapper.insertBatch(rows))) {
            throw new IllegalStateException("ACC交付件批量写入失败");
        }
        long persisted = mapper.selectCountByProjectId(command.projectId());
        if (persisted != rows.size()) {
            throw new IllegalStateException("ACC交付件初始化数量不完整");
        }
        return new DeliverableInitializationResult(rows.size(), Math.toIntExact(persisted));
    }

    @Override
    public List<DeliverableView> getByProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }
        return mapper.selectListByProjectId(projectId).stream()
                .map(row -> new DeliverableView(row.getId(), row.getProjectId(), row.getDeliverableCode(),
                        row.getName(), row.getStageCode(), row.getTaskCode(), row.getRequired(),
                        row.getSourceDefinitionId(), row.getStatus(), row.getVersion()))
                .toList();
    }

    private AccProjectDeliverableDO toDataObject(Long projectId, DeliverableDefinition definition) {
        if (definition == null || definition.deliverableCode() == null || definition.name() == null
                || definition.stageCode() == null) {
            throw new IllegalArgumentException("交付件定义不完整");
        }
        AccProjectDeliverableDO row = new AccProjectDeliverableDO();
        row.setProjectId(projectId);
        row.setDeliverableCode(definition.deliverableCode());
        row.setName(definition.name());
        row.setStageCode(definition.stageCode());
        row.setTaskCode(definition.taskCode());
        row.setRequired(definition.required());
        row.setSourceDefinitionId(definition.sourceDefinitionId());
        row.setStatus("PENDING");
        row.setVersion(0);
        return row;
    }
}
