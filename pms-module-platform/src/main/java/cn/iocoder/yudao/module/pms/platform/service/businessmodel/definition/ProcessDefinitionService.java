package cn.iocoder.yudao.module.pms.platform.service.businessmodel.definition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.definition.ProcessDefinitionSnapshot;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ExecutionBackendCapability;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.definition.ProcessDefinitionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.definition.PlatformProcessDefinitionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.definition.query.ProcessDefinitionPageQuery;
import tools.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中性过程定义与发布：读取统一目录校验实体/操作/字段，发布冻结版本；
 * 无后端声明支持所需规则语义时发布显式拒绝，不静默降级。
 */
@Service("pmsBusinessProcessDefinitionService")
public class ProcessDefinitionService implements ProcessDefinitionPort {

    private final PlatformProcessDefinitionMapper definitionMapper;
    private final BusinessModelCatalog catalog;
    private final List<ExecutionBackendCapability> backendCapabilities;

    public ProcessDefinitionService(PlatformProcessDefinitionMapper definitionMapper, BusinessModelCatalog catalog,
                                    List<ExecutionBackendCapability> backendCapabilities) {
        this.definitionMapper = definitionMapper;
        this.catalog = catalog;
        this.backendCapabilities = backendCapabilities;
    }

    @Transactional
    public ProcessDefinitionDO create(String definitionCode, String name, String ownerModule, String entityType,
                                      String operationCode, String ruleCode, String ruleVersion,
                                      List<BusinessFieldFilter> conditions, String resultType) {
        definitionMapper.selectDraft(definitionCode).ifPresent(existing -> {
            throw new BusinessContractException("DEFINITION_DRAFT_EXISTS",
                    "定义已存在草稿，请先发布或废弃: " + definitionCode);
        });
        BusinessModelDescriptor descriptor = catalog.require(ownerModule, entityType);
        if (descriptor.operations().stream().noneMatch(operation -> operation.code().equals(operationCode))) {
            throw new BusinessContractException("OPERATION_NOT_DECLARED",
                    "操作未在统一目录声明: " + operationCode);
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "过程定义至少需要一个字段条件");
        }
        for (BusinessFieldFilter condition : conditions) {
            if (descriptor.fields().stream()
                    .noneMatch(field -> field.code().equals(condition.fieldCode()) && field.readable())) {
                throw new BusinessContractException("FIELD_NOT_READABLE",
                        "条件字段不可读或不存在: " + condition.fieldCode());
            }
        }
        if (resultType == null || resultType.isBlank()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "过程定义必须声明结果类型");
        }
        ProcessDefinitionDO row = new ProcessDefinitionDO();
        row.setDefinitionCode(definitionCode);
        row.setDefinitionVersion(0);
        row.setName(name);
        row.setOwnerModule(ownerModule);
        row.setEntityType(entityType);
        row.setEntityStableCode(descriptor.stableCode());
        row.setEntityContractVersion(descriptor.contractVersion());
        row.setOperationCode(operationCode);
        descriptor.operations().stream().filter(operation -> operation.code().equals(operationCode))
                .findFirst().ifPresent(operation -> row.setOperationVersion(operation.version()));
        row.setRuleCode(ruleCode);
        row.setRuleVersion(ruleVersion);
        row.setConditionsJson(JsonUtils.toJsonString(conditions));
        row.setResultType(resultType);
        row.setStatus("DRAFT");
        definitionMapper.insert(row);
        return row;
    }

    @Transactional
    public ProcessDefinitionDO publish(Long id) {
        ProcessDefinitionDO row = definitionMapper.selectById(id);
        if (row == null) {
            throw new BusinessContractException("DEFINITION_NOT_FOUND", "过程定义不存在: " + id);
        }
        if (!"DRAFT".equals(row.getStatus())) {
            throw new BusinessContractException("DEFINITION_NOT_DRAFT", "仅草稿可以发布: " + row.getDefinitionCode());
        }
        // 发布时按当前目录复核，目录变化则显式拒绝，不让冻结值静默失效。
        BusinessModelDescriptor descriptor = catalog.require(row.getOwnerModule(), row.getEntityType());
        if (!Objects.equals(descriptor.stableCode(), row.getEntityStableCode())
                || descriptor.contractVersion() != row.getEntityContractVersion()) {
            throw new BusinessContractException("MODEL_VERSION_INCOMPATIBLE",
                    "实体契约已变化，请重新创建定义: " + row.getDefinitionCode());
        }
        if (descriptor.operations().stream()
                .noneMatch(operation -> operation.code().equals(row.getOperationCode())
                        && operation.version() == row.getOperationVersion())) {
            throw new BusinessContractException("OPERATION_VERSION_INCOMPATIBLE",
                    "操作版本已变化，请重新创建定义: " + row.getDefinitionCode());
        }
        if (backendCapabilities.stream()
                .noneMatch(capability -> capability.supportedSemantics().contains(row.getRuleCode()))) {
            throw new BusinessContractException("EXECUTION_BACKEND_UNAVAILABLE",
                    "没有执行后端声明支持规则语义: " + row.getRuleCode());
        }
        int nextVersion = definitionMapper.selectPublishedByCode(row.getDefinitionCode()).stream()
                .mapToInt(ProcessDefinitionDO::getDefinitionVersion)
                .max().orElse(0) + 1;
        row.setDefinitionVersion(nextVersion);
        row.setStatus("PUBLISHED");
        row.setPublishedAt(LocalDateTime.now());
        definitionMapper.updateById(row);
        return row;
    }

    public ProcessDefinitionDO requireById(Long id) {
        return Optional.ofNullable(definitionMapper.selectById(id))
                .orElseThrow(() -> new BusinessContractException("DEFINITION_NOT_FOUND", "过程定义不存在: " + id));
    }

    public PageResult<ProcessDefinitionDO> page(ProcessDefinitionPageQuery query) {
        return definitionMapper.selectPage(query);
    }

    @Override
    public Optional<ProcessDefinitionSnapshot> findPublished(String definitionCode) {
        return definitionMapper.selectPublishedByCode(definitionCode).stream()
                .findFirst()
                .map(ProcessDefinitionService::toSnapshot);
    }

    @Override
    public List<ProcessDefinitionSnapshot> listPublished(String ownerModule, String entityType) {
        return definitionMapper.selectPublished(ownerModule, entityType).stream()
                .map(ProcessDefinitionService::toSnapshot)
                .toList();
    }

    public static ProcessDefinitionSnapshot toSnapshot(ProcessDefinitionDO row) {
        List<BusinessFieldFilter> conditions = JsonUtils.parseObject(row.getConditionsJson(),
                new TypeReference<List<BusinessFieldFilter>>() {});
        return new ProcessDefinitionSnapshot(row.getDefinitionCode(), row.getDefinitionVersion(), row.getName(),
                row.getOwnerModule(), row.getEntityType(), row.getEntityStableCode(), row.getEntityContractVersion(),
                row.getOperationCode(), row.getOperationVersion(), row.getRuleCode(), row.getRuleVersion(),
                conditions, row.getResultType());
    }
}
