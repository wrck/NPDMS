package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.TemplateExecutionConfigurationCompilation;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultSubscriptionTaskContractTest {
    @Test void compilerReaderAndFactoryPreservePureSubscriptionsWithoutInventingAHandlingBinding() {
        var snapshot=ResultSubscriptionTaskFixture.snapshot();String stored=JsonUtils.toJsonString(snapshot);
        var roundTrip=TemplateExecutionSnapshotReader.read(stored);var task=roundTrip.getTasks().getFirst();
        assertNull(task.getBinding());assertNull(task.getPermission());assertTrue(ResultSubscriptionTaskContract.pure(task.getExecution()));
        var contract=ResultSubscriptionTaskFixture.contract(roundTrip);
        assertEquals("RESULT_SUBSCRIPTION",contract.getWorkBindingTypeCode());assertNull(contract.getTargetObjectKey());assertNull(contract.getTargetContextCode());
        assertEquals(ResultSubscriptionTaskContract.parameters(),contract.getBindingParameterSnapshot());
        assertFalse(contract.getBindingParameterSnapshot().contains("policy"));assertFalse(contract.getBindingParameterSnapshot().contains("subscriptions"));
        assertDoesNotThrow(()->ResultSubscriptionTaskContract.requireRuntime(contract));
        assertTrue(ResultSubscriptionTaskContract.matches(contract,task));
        assertEquals(stored,JsonUtils.toJsonString(snapshot));
        var graph=mock(ProjectRuntimeGraphMapper.class);var stages=mock(ProjectStageExecutionContractMapper.class);
        assertDoesNotThrow(()->new ProjectRuntimeGraphFreezer(graph,stages).validate(roundTrip));verifyNoInteractions(graph,stages);
    }
    @Test void versionedPolicyChangesDoNotCreateASecondEditableWorkBindingTruth() {
        var snapshot=ResultSubscriptionTaskFixture.snapshot();var first=ResultSubscriptionTaskFixture.contract(snapshot);
        ((ObjectNode)snapshot.getTasks().getFirst().getExecution().get("subscriptions").get(0).get("policy")).put("selection","ANY_MATCHING");
        TemplateVersionSnapshot.validate(snapshot);var next=ResultSubscriptionTaskFixture.contract(snapshot);
        assertEquals(first.getBindingParameterSnapshot(),next.getBindingParameterSnapshot());assertEquals(first.getPermissionSnapshot(),next.getPermissionSnapshot());
        assertTrue(ResultSubscriptionTaskContract.matches(next,snapshot.getTasks().getFirst()));
    }
    @Test void originalNativeTasksAndOldFormatCannotBeSilentlyReclassifiedAsSubscriptions() {
        var snapshot=ResultSubscriptionTaskFixture.snapshot();snapshot.setExecutionSchemaVersion(2);
        assertThrows(IllegalArgumentException.class,()->TemplateExecutionSnapshotReader.read(JsonUtils.toJsonString(snapshot)));
        var original=new TaskExecutionContractFactory().createTaskNative(4L,java.time.LocalDateTime.now());
        assertEquals("TASK_NATIVE",original.getWorkBindingTypeCode());assertThrows(IllegalArgumentException.class,()->ResultSubscriptionTaskContract.requireRuntime(original));
        var source=ResultSubscriptionTaskFixture.designer();source.getTasks().getFirst().setExecution(null);
        assertFalse(ResultSubscriptionTaskFixture.compiler().compileVersioned(source).valid());
    }
    @ParameterizedTest @ValueSource(strings={"empty","native-rule","permission","manual-type"})
    void aMissingBindingIsAllowedOnlyForAnExplicitPureSubscription(String damage) {
        var source=ResultSubscriptionTaskFixture.designer();var task=source.getTasks().getFirst();
        switch(damage){
            case "empty" -> task.setExecution(JsonUtils.parseTree("{\"subscriptions\":[]}"));
            case "native-rule" -> task.getCompletionRule().setExpression(JsonUtils.parseTree("{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"PENDING_ACCEPT\"}}"));
            case "permission" -> {var policy=new TemplateDesignerDocument.PermissionRequirement();policy.setPolicyRef("PROJECT_TASK_NATIVE_DEFAULT");task.setPermission(policy);}
            case "manual-type" -> {var binding=new TemplateDesignerDocument.WorkBindingSpec();binding.setType("RESULT_SUBSCRIPTION");task.setWorkBinding(binding);}
            default -> throw new AssertionError(damage);
        }
        assertFalse(ResultSubscriptionTaskFixture.compiler().compileVersioned(source).valid());
    }
    @ParameterizedTest @ValueSource(strings={"owner","target","component","form","view","permission","version","key","parameters","approval"})
    void storedCompatibilityIdentityDoesNotAuthorizeAnExternalOwnerOrInventAnotherContract(String damage) {
        var contract=ResultSubscriptionTaskFixture.contract(ResultSubscriptionTaskFixture.snapshot());
        switch(damage){
            case "owner" -> contract.setTargetContextCode("ACC");
            case "target" -> contract.setTargetObjectKey("a");
            case "component" -> contract.setComponentKey("page");
            case "form" -> contract.setDynamicFormRevisionId(1L);
            case "view" -> contract.setBindingViewSnapshot("{}");
            case "permission" -> contract.setPermissionPolicyRef("ADMIN");
            case "version" -> contract.setSourceDefinitionVersion(2);
            case "key" -> contract.setSourceNodeKey(null);
            case "parameters" -> contract.setBindingParameterSnapshot("{}");
            case "approval" -> contract.setApprovalInstanceId(1L);
            default -> throw new AssertionError(damage);
        }
        assertThrows(IllegalArgumentException.class,()->ResultSubscriptionTaskContract.requireRuntime(contract));
    }
    @Test void theFactoryValidatesTheEphemeralProjectionBeforeStoringItsMinimalMarker() {
        var definition=ResultSubscriptionTaskFixture.snapshot().toRuntimeContent().getTasks().getFirst();
        new TaskExecutionContractFactory().validateDefinition(definition);
        definition.setBindingConfig("{}");assertThrows(IllegalArgumentException.class,()->new TaskExecutionContractFactory().validateDefinition(definition));
    }
    @Test void thisIncrementDoesNotEnableUnfinishedSubscriptionPublication() {
        var issues=new TemplateExecutionConfigurationCompilation(mock(cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry.class)).prepare(ResultSubscriptionTaskFixture.designer());
        assertTrue(issues.stream().anyMatch(issue->"RESULT_SUBSCRIPTION_NOT_INSTALLED".equals(issue.code())));
    }
}
