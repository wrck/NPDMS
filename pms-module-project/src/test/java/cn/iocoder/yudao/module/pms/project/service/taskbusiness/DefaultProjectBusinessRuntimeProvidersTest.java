package cn.iocoder.yudao.module.pms.project.service.taskbusiness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessCompletionEvaluator;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DefaultProjectBusinessRuntimeProvidersTest {
    @Test void manualCandidateLookupUsesTheSameFrozenSelectorsAsAutomaticAssociation(){
        var api=mock(ProjectBusinessRuntimeApi.class);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(api);
        var type=new ProjectBusinessRuntimeApi.Type("ACC","acceptanceActivity");
        when(api.definitions()).thenReturn(List.of(new ProjectBusinessRuntimeApi.Definition(type,"ACC_ACCEPTANCE_ACTIVITY","Acceptance",Map.of(),"ACCEPTANCE")));
        String parameters="{\"acceptanceType\":\"FINAL\",\"configurationName\":\"final-review\"}";
        var query=new ProjectBusinessRuntimeApi.Candidates(7L,20L,type,null,100,parameters);
        when(api.candidates(query)).thenReturn(List.of());
        var value=new TaskBusinessProviderRegistry(new DefaultProjectBusinessRuntimeProviders(provider,mock(ProjectNodeExecutionApi.class)).withDefaults(List.of()))
                .require("ACC","acceptanceActivity");
        assertTrue(value.candidates(new TaskBusinessObjectProvider.Context(7L,42L,20L,3L,"trace",parameters)).isEmpty());
        verify(api).candidates(query);
    }
    @Test void frozenBusinessParametersReachTheInheritedOwnerForAutomaticAssociation(){
        var api=mock(ProjectBusinessRuntimeApi.class);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(api);
        var type=new ProjectBusinessRuntimeApi.Type("ACC","acceptanceActivity");
        when(api.definitions()).thenReturn(List.of(new ProjectBusinessRuntimeApi.Definition(type,"ACC_ACCEPTANCE_ACTIVITY","Acceptance",Map.of(),"ACCEPTANCE")));
        String parameters="{\"acceptanceType\":\"FINAL\",\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}";
        var query=new ProjectBusinessRuntimeApi.Candidates(7L,20L,type,10L,100,parameters);
        when(api.candidates(query)).thenReturn(List.of(new ProjectBusinessRuntimeApi.Reference(12L,"final-v1")));
        var defaults=new DefaultProjectBusinessRuntimeProviders(provider,mock(ProjectNodeExecutionApi.class));
        var value=new TaskBusinessProviderRegistry(defaults.withDefaults(List.of())).require("ACC","acceptanceActivity");
        assertEquals(List.of(new TaskBusinessObjectProvider.AssociationCandidate("12","final-v1")),value.associationCandidates(new TaskBusinessObjectProvider.AssociationContext(7L,20L,"ACC_ACCEPTANCE_ACTIVITY",parameters),"10",100));
        verify(api).candidates(query);
    }
    @Test void twoServiceDefinitionsGainRuntimeFactsAndDynamicDeliveryTypesWithoutBusinessAdapters(){
        var api=mock(ProjectBusinessRuntimeApi.class);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(api);
        var first=new ProjectBusinessRuntimeApi.Definition(new ProjectBusinessRuntimeApi.Type("IT","note"),"IT_NOTE","Note",Map.of("BUSINESS_RECORD_SAVED","Saved"),null);
        var second=new ProjectBusinessRuntimeApi.Definition(new ProjectBusinessRuntimeApi.Type("IT","other"),"IT_OTHER","Other",Map.of("BUSINESS_RECORD_SAVED","Saved"),null);
        when(api.definitions()).thenReturn(List.of(first,second));
        var defaults=new DefaultProjectBusinessRuntimeProviders(provider,mock(ProjectNodeExecutionApi.class));var registry=new TaskBusinessProviderRegistry(defaults.withDefaults(List.of()));
        assertTrue(registry.supportsCompletionFact("IT","note","BUSINESS_RECORD_SAVED"));
        assertTrue(registry.supportsStageCompletionFact("IT","other",ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        assertFalse(registry.supportsCompletionFact("OTHER","note",ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT"));
        assertFalse(registry.supportsCompletionFact("IT","note",ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"../../secret"));
        assertThrows(IllegalStateException.class,()->registry.require("IT","note").lockAndRevalidate(new TaskBusinessObjectProvider.Context(7L,42L,20L,3L,"trace"),"11","version"));
    }
    @Test void missingDeliveryIsFalseOnlyAfterCompleteExactOwnerObservationAndUnavailableStaysUnknown(){
        String code=ProjectBusinessRuntimeApi.DELIVERY_PREFIX+"REPORT";
        var leaf=new RuleProgram.Leaf("1","rule","BUSINESS_FACT",JsonUtils.parseTree("{\"factCode\":\""+code+"\",\"quantifier\":\"ALL\"}"));
        var missing=new TaskBusinessLinkFact(1L,"11","Note","version",Map.of(ProjectBusinessRuntimeApi.DELIVERY_COMPLETE,true),List.of(),Set.of());
        var unknown=new TaskBusinessLinkFact(1L,"11","Note","version",Map.of(),List.of(),Set.of());
        assertEquals(cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact.known(false),TaskBusinessCompletionEvaluator.businessFact(leaf,List.of(missing),new ArrayList<>(),new ArrayList<>()));
        assertEquals(cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact.unknown("BUSINESS_FACT_UNKNOWN"),TaskBusinessCompletionEvaluator.businessFact(leaf,List.of(unknown),new ArrayList<>(),new ArrayList<>()));
    }
    @Test void onlyTheConfiguredUploadedTypeCanSupplyHandlingWithoutAnExtraBusinessSubmit(){
        var leaf=new RuleProgram.Leaf("1","rule","BUSINESS_FACT",JsonUtils.parseTree("{\"factCode\":\"BUSINESS_DELIVERY_UPLOADED:REPORT\",\"quantifier\":\"ALL\"}"));
        var program=new RuleProgram(cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule.Kind.CONDITION,"unused",List.of(leaf));
        var uploaded=new TaskBusinessLinkFact(1L,"11","Note","version",Map.of("BUSINESS_DELIVERY_UPLOADED:REPORT",true),List.of(),Set.of());
        var wrongType=new TaskBusinessLinkFact(1L,"11","Note","version",Map.of("BUSINESS_DELIVERY_UPLOADED:PHOTO",true),List.of(),Set.of());
        assertTrue(TaskBusinessCompletionEvaluator.hasCompletedHandling(false,"task",List.of(uploaded),program));
        assertFalse(TaskBusinessCompletionEvaluator.hasCompletedHandling(false,"task",List.of(wrongType),program));
        assertFalse(TaskBusinessCompletionEvaluator.hasCompletedHandling(false,"task",List.of(uploaded)));
    }
    @Test void nativeOwnerFactsAndActionsSurviveSharedDeliveryEnrichment(){
        var api=mock(ProjectBusinessRuntimeApi.class);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(api);
        var type=new ProjectBusinessRuntimeApi.Type("SOL","siteSurvey");
        when(api.definitions()).thenReturn(List.of(new ProjectBusinessRuntimeApi.Definition(type,"SOL_SITE_SURVEY","Survey",Map.of("SURVEY_CONFIRMED","Confirmed"),"SITE_SURVEY")));
        var nativeOwner=mock(TaskBusinessObjectProvider.class);when(nativeOwner.ownerContext()).thenReturn("SOL");when(nativeOwner.objectType()).thenReturn("SITE_SURVEY");
        when(nativeOwner.completionFactCodes()).thenReturn(Set.of("SURVEY_CONFIRMED"));when(nativeOwner.completionFactLabels()).thenReturn(Map.of("SURVEY_CONFIRMED","Confirmed"));
        var context=new TaskBusinessObjectProvider.Context(7L,42L,20L,3L,"trace");
        var original=new TaskBusinessObjectProvider.BusinessObjectFact("11","Original survey","native-v1",Set.of("QUERY"),Map.of("SURVEY_CONFIRMED",false),List.of());
        when(nativeOwner.inspect(context,"11")).thenReturn(original);
        when(api.deliveryFacts(new ProjectBusinessRuntimeApi.Query(7L,20L,type,11L),false)).thenReturn(new ProjectBusinessRuntimeApi.DeliveryFacts(Map.of("BUSINESS_DELIVERY_UPLOADED:REPORT",true),"file-v1"));
        var defaults=new DefaultProjectBusinessRuntimeProviders(provider,mock(ProjectNodeExecutionApi.class));
        var registry=new TaskBusinessProviderRegistry(defaults.withDefaults(List.of(nativeOwner)));
        var fact=registry.require("SOL","SITE_SURVEY").inspect(context,"11");
        assertEquals(Set.of("QUERY"),fact.allowedActions());assertEquals("Original survey",fact.displayName());
        assertEquals(false,fact.completionFacts().get("SURVEY_CONFIRMED"));assertEquals(true,fact.completionFacts().get("BUSINESS_DELIVERY_UPLOADED:REPORT"));
        assertTrue(registry.supportsCompletionFact("SOL","SITE_SURVEY","BUSINESS_DELIVERY_UPLOADED:REPORT"));
        verify(nativeOwner).inspect(context,"11");verify(api,never()).inspectForUser(any(),any(),anyBoolean(),any());
    }
    @Test void canonicalContextUsesOneOwnerInspectionAndKeepsAllDeliveryEvidence(){
        var api=mock(ProjectBusinessRuntimeApi.class);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(api);
        var type=new ProjectBusinessRuntimeApi.Type("SOL","siteSurvey");
        when(api.definitions()).thenReturn(List.of(new ProjectBusinessRuntimeApi.Definition(type,"SOL_SITE_SURVEY","Survey",Map.of("SURVEY_CONFIRMED","Confirmed"),"SITE_SURVEY")));
        var context=new TaskBusinessObjectProvider.Context(7L,42L,20L,3L,"trace","{\"kind\":\"FINAL\"}");
        var user=new ProjectBusinessRuntimeApi.UserContext(7L,42L,20L,type,context.bindingParameters());
        when(api.inspectContextAndObjects(user,List.of(11L,12L))).thenReturn(new ProjectBusinessRuntimeApi.UserInspection(Set.of("QUERY"),List.of(
                new ProjectBusinessRuntimeApi.Observation(11L,"v1",Map.of("SURVEY_CONFIRMED",true),true,null),
                new ProjectBusinessRuntimeApi.Observation(12L,"v2",Map.of("SURVEY_CONFIRMED",false),false,null))));
        for(long id:List.of(11L,12L))when(api.deliveryFacts(new ProjectBusinessRuntimeApi.Query(7L,20L,type,id),false))
                .thenReturn(new ProjectBusinessRuntimeApi.DeliveryFacts(Map.of(ProjectBusinessRuntimeApi.DELIVERY_COMPLETE,true),"file-"+id));
        var value=new TaskBusinessProviderRegistry(new DefaultProjectBusinessRuntimeProviders(provider,mock(ProjectNodeExecutionApi.class)).withDefaults(List.of())).require("SOL","siteSurvey");
        var result=value.inspectContextAndObjects(context,List.of("11","12"));
        assertEquals(Set.of("QUERY"),result.allowedActions());assertEquals(List.of("11","12"),result.objects().stream().map(TaskBusinessObjectProvider.BusinessObjectFact::objectId).toList());
        assertEquals(true,result.objects().getFirst().completionFacts().get("SURVEY_CONFIRMED"));assertEquals(false,result.objects().getLast().completionFacts().get("SURVEY_CONFIRMED"));
        result.objects().forEach(fact->{assertEquals(Set.of("QUERY"),fact.allowedActions());assertEquals(true,fact.completionFacts().get(ProjectBusinessRuntimeApi.DELIVERY_COMPLETE));});
        verify(api).inspectContextAndObjects(user,List.of(11L,12L));verify(api,never()).actions(any());verify(api,never()).inspectForUser(any(),any(),anyBoolean(),any());
        verify(api,times(2)).deliveryFacts(any(),eq(false));
        when(api.deliveryFacts(new ProjectBusinessRuntimeApi.Query(7L,20L,type,12L),false)).thenThrow(new IllegalStateException("evidence unavailable"));
        assertThrows(IllegalStateException.class,()->value.inspectContextAndObjects(context,List.of("11","12")));
    }
    @Test void deliveryWrapperPreservesExistingOwnersBatchedAuthorizationAndArtifacts(){
        var api=mock(ProjectBusinessRuntimeApi.class);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(api);
        var type=new ProjectBusinessRuntimeApi.Type("SOL","siteSurvey");
        when(api.definitions()).thenReturn(List.of(new ProjectBusinessRuntimeApi.Definition(type,"SOL_SITE_SURVEY","Survey",Map.of(),"SITE_SURVEY")));
        var owner=mock(TaskBusinessObjectProvider.class);when(owner.ownerContext()).thenReturn("SOL");when(owner.objectType()).thenReturn("SITE_SURVEY");
        var context=new TaskBusinessObjectProvider.Context(7L,42L,20L,3L,"trace");
        var artifact=new TaskBusinessObjectProvider.BusinessArtifact("file",1,"reference","report","v1");
        var fact=new TaskBusinessObjectProvider.BusinessObjectFact("11","Survey","v1",Set.of("QUERY"),Map.of("SURVEY_CONFIRMED",true),List.of(artifact));
        when(owner.inspectContextAndObjects(context,List.of("11"))).thenReturn(new TaskBusinessObjectProvider.BusinessObjectInspection(Set.of("QUERY","CREATE"),List.of(fact)));
        when(api.deliveryFacts(new ProjectBusinessRuntimeApi.Query(7L,20L,type,11L),false)).thenReturn(new ProjectBusinessRuntimeApi.DeliveryFacts(Map.of(ProjectBusinessRuntimeApi.DELIVERY_COMPLETE,true),"files"));
        var value=new TaskBusinessProviderRegistry(new DefaultProjectBusinessRuntimeProviders(provider,mock(ProjectNodeExecutionApi.class)).withDefaults(List.of(owner))).require("SOL","SITE_SURVEY");
        var result=value.inspectContextAndObjects(context,List.of("11"));
        assertEquals(Set.of("QUERY","CREATE"),result.allowedActions());assertEquals(List.of(artifact),result.objects().getFirst().artifacts());assertEquals(Set.of("QUERY"),result.objects().getFirst().allowedActions());
        verify(owner).inspectContextAndObjects(context,List.of("11"));verify(owner,never()).inspectContext(any());verify(owner,never()).inspect(any(),any());verify(api,never()).inspectContextAndObjects(any(),any());
    }
}
