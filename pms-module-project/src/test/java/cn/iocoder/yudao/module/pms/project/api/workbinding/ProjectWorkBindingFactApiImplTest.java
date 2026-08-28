package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTemplateRevisionFactRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkBindingFactApiImplTest {

    private static final String BINDING = "{\"schemaVersion\":1,\"preparationTemplateCode\":"
            + "\"PRE_02_SITE_SURVEY\",\"preparationTemplateRevision\":1,"
            + "\"fixedFormCatalogVersion\":1,\"itemConfiguration\":["
            + item("POWER", 10) + "," + item("NETWORK_PORT", 20) + "," + item("FIBER", 30) + ","
            + item("CABINET", 40) + "," + item("NETWORK_CABLE", 50) + ","
            + item("OPTICAL_MODULE", 60) + "]}";
    private static final String BINDING_WITH_EXTENSION = BINDING.replace("]}",
            "," + extensionItem() + "]}");
    private static final String REQUIREMENT_ANALYSIS_BINDING = "{\"schemaVersion\":2,"
            + "\"dynamicFormTemplateId\":700,\"dynamicFormTemplateRevisionId\":701,"
            + "\"dynamicFormRevisionNo\":3,\"dynamicFormRevisionFactVersion\":9}";

    @Mock
    private ProjectMasterMapper projectMapper;
    @Mock
    private ProjectWorkBindingFactMapper factMapper;
    private ProjectWorkBindingFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        api = new ProjectWorkBindingFactApiImpl(projectMapper, factMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void inspectReturnsTheOnlyExactCurrentContract() {
        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of(record(0L, BINDING)));

        var fact = api.inspect(new ProjectWorkBindingFactQuery(100L));

        assertEquals(101L, fact.projectTaskId());
        assertEquals(102L, fact.executionContractId());
        assertEquals(11, fact.projectVersion());
        assertEquals("PRE_02_SITE_SURVEY", fact.preparationTemplateCode());
        ArgumentCaptor<ProjectWorkBindingFactLookupQuery> captor =
                ArgumentCaptor.forClass(ProjectWorkBindingFactLookupQuery.class);
        verify(factMapper).selectCurrentFacts(captor.capture());
        assertEquals(0L, captor.getValue().tenantId());
        assertEquals("BUSINESS_OBJECT", captor.getValue().workBindingTypeCode());
        assertEquals("SOL", captor.getValue().targetContextCode());
    }

    @Test
    void frozenApprovedExtensionRemainsReadableWithoutDictionaryLookup() {
        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of(record(0L, BINDING_WITH_EXTENSION)));
        assertEquals(7, JsonUtils.parseObject(api.inspect(new ProjectWorkBindingFactQuery(100L))
                .itemConfigurationSnapshot(), List.class).size());

        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project(0L, 11));
        when(factMapper.selectProjectTaskForUpdate(any())).thenReturn(task(0L, 7));
        when(factMapper.selectCurrentContractForUpdate(any())).thenReturn(contract(0L, 3, BINDING_WITH_EXTENSION));
        when(factMapper.selectTemplateRevisionFact(any())).thenReturn(templateRevision());
        assertEquals(7, JsonUtils.parseObject(api.lockAndRevalidate(new ProjectWorkBindingFactRevalidationQuery(
                100L, 101L, 102L, 7, 3, 11)).itemConfigurationSnapshot(), List.class).size());
    }

    @Test
    void inspectRejectsMissingAmbiguousCrossTenantAndInvalidFrozenJson() {
        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectWorkBindingFactQuery(100L)));

        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of(record(0L, BINDING), record(0L, BINDING)));
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectWorkBindingFactQuery(100L)));

        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of(record(1L, BINDING)));
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectWorkBindingFactQuery(100L)));

        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of(record(0L, "{}")));
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectWorkBindingFactQuery(100L)));
    }

    @Test
    void lockAndRevalidateLocksProjectThenTaskThenCurrentContract() {
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project(0L, 11));
        when(factMapper.selectProjectTaskForUpdate(any())).thenReturn(task(0L, 7));
        when(factMapper.selectCurrentContractForUpdate(any())).thenReturn(contract(0L, 3, BINDING));
        when(factMapper.selectTemplateRevisionFact(any())).thenReturn(templateRevision());

        var fact = api.lockAndRevalidate(new ProjectWorkBindingFactRevalidationQuery(
                100L, 101L, 102L, 7, 3, 11));

        assertEquals(7, fact.projectTaskVersion());
        assertEquals(3, fact.contractVersion());
        verify(projectMapper).selectByIdForUpdate(100L);
        ArgumentCaptor<ProjectWorkBindingFactLockQuery> captor =
                ArgumentCaptor.forClass(ProjectWorkBindingFactLockQuery.class);
        verify(factMapper).selectProjectTaskForUpdate(captor.capture());
        verify(factMapper).selectCurrentContractForUpdate(captor.getValue());
    }

    @Test
    void requirementAnalysisUsesControlledTupleAndReturnsRawFrozenSnapshot() {
        when(factMapper.selectCurrentFacts(any())).thenReturn(List.of(requirementAnalysisRecord()));

        var fact = api.inspect(new ProjectWorkBindingFactQuery(
                100L, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));

        assertEquals("REQUIREMENT_ANALYSIS", fact.targetObjectType());
        assertEquals(REQUIREMENT_ANALYSIS_BINDING, fact.bindingParameterSnapshot());
        assertEquals(900L, fact.templateRevisionId());
        assertEquals(2, fact.templateRevisionNo());
        assertEquals(null, fact.preparationTemplateCode());
        assertEquals(700L, fact.dynamicFormTemplateId());
        assertEquals(701L, fact.dynamicFormTemplateRevisionId());
        assertEquals(3, fact.dynamicFormRevisionNo());
        assertEquals(9, fact.dynamicFormRevisionFactVersion());

        ProjectWorkBindingTarget unsupported = new ProjectWorkBindingTarget(
                "BUSINESS_OBJECT", "SOL", "OTHER", "OTHER");
        assertThrows(ServiceException.class,
                () -> api.inspect(new ProjectWorkBindingFactQuery(100L, unsupported)));
    }

    @Test
    void requirementAnalysisLockRevalidatesTheSameControlledTuple() {
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project(0L, 11));
        when(factMapper.selectProjectTaskForUpdate(any())).thenReturn(task(0L, 7));
        when(factMapper.selectCurrentContractForUpdate(any())).thenReturn(requirementAnalysisContract());
        when(factMapper.selectTemplateRevisionFact(any())).thenReturn(templateRevision());

        var fact = api.lockAndRevalidate(new ProjectWorkBindingFactRevalidationQuery(
                100L, 101L, 102L, 7, 3, 11, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));

        assertEquals(REQUIREMENT_ANALYSIS_BINDING, fact.bindingParameterSnapshot());
        assertEquals("PRE_04_REQUIREMENT_ANALYSIS", fact.targetObjectKey());
        assertEquals(701L, fact.dynamicFormTemplateRevisionId());
    }

    @Test
    void lockAndRevalidateRejectsVersionChangesBeforeLaterLocks() {
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project(0L, 12));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(
                new ProjectWorkBindingFactRevalidationQuery(100L, 101L, 102L, 7, 3, 11)));
        verify(factMapper, never()).selectProjectTaskForUpdate(any());

        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project(0L, 11));
        when(factMapper.selectProjectTaskForUpdate(any())).thenReturn(task(0L, 8));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(
                new ProjectWorkBindingFactRevalidationQuery(100L, 101L, 102L, 7, 3, 11)));
        verify(factMapper, never()).selectCurrentContractForUpdate(any());
    }

    @Test
    void rejectsMissingTrustedTenantAndTaskNativeContract() {
        TenantContextHolder.clear();
        assertThrows(ServiceException.class, () -> api.inspect(new ProjectWorkBindingFactQuery(100L)));

        TenantContextHolder.setTenantId(0L);
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project(0L, 11));
        when(factMapper.selectProjectTaskForUpdate(any())).thenReturn(task(0L, 7));
        ProjectTaskExecutionContractDO nativeContract = contract(0L, 3, BINDING);
        nativeContract.setWorkBindingTypeCode("TASK_NATIVE");
        when(factMapper.selectCurrentContractForUpdate(any())).thenReturn(nativeContract);
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(
                new ProjectWorkBindingFactRevalidationQuery(100L, 101L, 102L, 7, 3, 11)));
    }

    private static ProjectWorkBindingFactRecord record(long tenantId, String binding) {
        return new ProjectWorkBindingFactRecord(tenantId, 100L, 11, 101L, 7, 501L,
                102L, 501L, "BUSINESS_OBJECT", "SOL", "SITE_SURVEY_PREPARATION",
                "PRE_02_SITE_SURVEY", binding, 2, 3, 900L, 2);
    }

    private static ProjectWorkBindingFactRecord requirementAnalysisRecord() {
        return new ProjectWorkBindingFactRecord(0L, 100L, 11, 101L, 7, 501L,
                102L, 501L, "BUSINESS_OBJECT", "SOL", "REQUIREMENT_ANALYSIS",
                "PRE_04_REQUIREMENT_ANALYSIS", REQUIREMENT_ANALYSIS_BINDING, 2, 3, 900L, 2);
    }

    private static ProjectMasterDO project(long tenantId, int version) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setTenantId(tenantId);
        project.setVersion(version);
        return project;
    }

    private static ProjectTaskInstanceDO task(long tenantId, int version) {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(101L);
        task.setProjectId(100L);
        task.setSourceDefinitionId(501L);
        task.setTenantId(tenantId);
        task.setVersion(version);
        return task;
    }

    private static ProjectTaskExecutionContractDO contract(long tenantId, int version, String binding) {
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setId(102L);
        contract.setProjectTaskId(101L);
        contract.setTemplateTaskDefinitionId(501L);
        contract.setWorkBindingTypeCode("BUSINESS_OBJECT");
        contract.setTargetContextCode("SOL");
        contract.setTargetObjectType("SITE_SURVEY_PREPARATION");
        contract.setTargetObjectKey("PRE_02_SITE_SURVEY");
        contract.setBindingParameterSnapshot(binding);
        contract.setSourceDefinitionVersion(2);
        contract.setContractVersion(version);
        contract.setTenantId(tenantId);
        return contract;
    }

    private static ProjectTemplateRevisionFactRecord templateRevision() {
        return new ProjectTemplateRevisionFactRecord(501L, 900L, 2);
    }

    private static ProjectTaskExecutionContractDO requirementAnalysisContract() {
        ProjectTaskExecutionContractDO contract = contract(0L, 3, REQUIREMENT_ANALYSIS_BINDING);
        contract.setTargetObjectType("REQUIREMENT_ANALYSIS");
        contract.setTargetObjectKey("PRE_04_REQUIREMENT_ANALYSIS");
        return contract;
    }

    private static String item(String code, int sortOrder) {
        return "{\"itemCode\":\"" + code + "\",\"itemName\":\"" + code
                + "\",\"enabled\":true,\"formCode\":\"" + code
                + "\",\"formVersion\":1,\"evidenceRequired\":false,"
                + "\"sourceRequirementCode\":\"NONE\",\"waiverAllowed\":false,"
                + "\"approvalRoleCode\":\"SERVICE_MANAGER_L1\",\"sortOrder\":" + sortOrder + "}";
    }

    private static String extensionItem() {
        return "{\"itemCode\":\"GROUNDING\",\"itemName\":\"接地\",\"enabled\":true,"
                + "\"formCode\":\"POWER\",\"formVersion\":1,\"evidenceRequired\":true,"
                + "\"sourceRequirementCode\":\"NONE\",\"waiverAllowed\":false,"
                + "\"approvalRoleCode\":\"SERVICE_MANAGER_L1\",\"sortOrder\":70}";
    }
}
