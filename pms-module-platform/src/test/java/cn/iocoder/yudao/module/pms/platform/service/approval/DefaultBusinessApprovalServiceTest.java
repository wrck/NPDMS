package cn.iocoder.yudao.module.pms.platform.service.approval;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.query.DefaultApprovalQuery;
import cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalApi.*;
import static cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.DefaultBusinessApprovalProcessApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultBusinessApprovalServiceTest {
    private final EntityRef entity=new EntityRef(1L,"IMP","arrival",11L);
    private final DirectBusinessOwners owners=mock(DirectBusinessOwners.class);
    private final ProjectBusinessService<?> owner=mock(ProjectBusinessService.class);
    private final BusinessCallerContext callers=mock(BusinessCallerContext.class);
    private final ApprovalAttemptMapper attempts=mock(ApprovalAttemptMapper.class);
    private final ApprovalOpinionMapper opinions=mock(ApprovalOpinionMapper.class);
    private final DefaultBusinessApprovalProcessApi processes=mock(DefaultBusinessApprovalProcessApi.class);
    private final BusinessEventPort events=mock(BusinessEventPort.class);
    private final Map<Long,ApprovalAttemptDO> rows=new LinkedHashMap<>();
    private final DefaultBusinessApprovalService service=new DefaultBusinessApprovalService(owners,callers,attempts,opinions,processes,events);

    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        when(callers.require()).thenReturn(new AbstractBusinessApplicationService.ResolvedCaller(1L,9L,"test"));
        doReturn(Optional.of(owner)).when(owners).byIdentity("IMP","arrival");
        when(owner.approvalSubject(eq(11L),anyBoolean(),anyBoolean())).thenReturn(new Subject(entity,20L,3L,Map.of("quantity",1)));
        when(attempts.selectDefaultAttempts(any())).thenAnswer(call->rows.values().stream().filter(row->matches(row,call.getArgument(0))).sorted(Comparator.comparing(ApprovalAttemptDO::getId).reversed()).toList());
        when(attempts.lockDefaultAttempt(any())).thenAnswer(call->rows.values().stream().filter(row->matches(row,call.getArgument(0)) && Objects.equals(row.getAttemptId(),((DefaultApprovalQuery)call.getArgument(0)).attemptId())).findFirst().orElse(null));
        when(attempts.selectById(anyLong())).thenAnswer(call->rows.get(call.getArgument(0)));
        when(attempts.insert(any(ApprovalAttemptDO.class))).thenAnswer(call->{var row=(ApprovalAttemptDO)call.getArgument(0);row.setId(100L+rows.size());row.setCreateTime(LocalDateTime.now());rows.put(row.getId(),row);return 1;});
        when(attempts.updateById(any(ApprovalAttemptDO.class))).thenAnswer(call->{var row=(ApprovalAttemptDO)call.getArgument(0);row.setVersion(row.getVersion()+1);return 1;});
        when(processes.start(any())).thenAnswer(call->{var command=(Command)call.getArgument(0);return new Result(command.context(),command.definitionId(),"instance-"+command.context().attemptId(),"PENDING",null);});
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    private Start request(String key,long version){return new Start("published:1",version,key,Map.of("purpose","审查"),Map.of("review",List.of(8L)));}
    private boolean matches(ApprovalAttemptDO row,DefaultApprovalQuery q){return Objects.equals(row.getTenantId(),q.tenantId())&&Objects.equals(row.getOwnerModule(),q.ownerModule())&&Objects.equals(row.getEntityType(),q.entityType())&&Objects.equals(row.getEntityId(),q.entityId())&&Objects.equals(row.getPurpose(),q.purpose())&&Objects.equals(row.getBackendId(),q.backendId());}
    private Result result(String key,String state){return new Result(new Context(entity,20L,3L,key,9L),"published:1","instance-"+key,state,"审批意见");}

    @Test void staleContentCannotStartAWorkflow(){
        assertThrows(BusinessContractException.class,()->service.start(entity,request("old",2)));
        verify(processes,never()).start(any());assertTrue(rows.isEmpty());
    }
    @Test void anotherKeyCannotDuplicateThePendingSubmission(){
        service.start(entity,request("first",3));
        assertThrows(BusinessContractException.class,()->service.start(entity,request("second",3)));
        verify(processes,times(1)).start(any());assertEquals(1,rows.size());
    }
    @Test void retryReturnsTheSameAttemptAndBindsTheWholeIntent(){
        var first=service.start(entity,request("retry",3));var replay=service.start(entity,request("retry",3));
        assertEquals(first.id(),replay.id());verify(processes,times(1)).start(any());
        assertThrows(BusinessContractException.class,()->service.start(entity,new Start("another:2",3L,"retry",Map.of(),Map.of())));
        verify(owner,atLeast(2)).approvalSubject(11L,true,true);
    }
    @Test void synchronousCompletionIsRecordedAndDuplicateCallbacksDoNotRepeatEffects(){
        doReturn(result("auto","APPROVED")).when(processes).start(any());
        var attempt=service.start(entity,request("auto",3));assertEquals("APPROVED",attempt.status());
        service.record(result("auto","APPROVED"));
        verify(events,times(1)).append(any());verify(opinions,times(2)).insert(any(ApprovalOpinionDO.class));
    }
    @Test void oldConclusionAndOpinionsSurviveAResubmission(){
        var first=service.start(entity,request("first",3));service.record(result("first","REJECTED"));
        var second=service.start(entity,request("second",3));assertEquals(first.id(),second.previousAttemptId());
        assertEquals("REJECTED",rows.get(first.id()).getStatus());assertEquals("PENDING",rows.get(second.id()).getStatus());
        assertThrows(BusinessContractException.class,()->service.record(result("first","APPROVED")));
        assertEquals("REJECTED",rows.get(first.id()).getStatus());
    }
    @Test void anApprovalOnAnOlderBusinessVersionIsShownAsStale(){
        service.start(entity,request("first",3));service.record(result("first","APPROVED"));
        when(owner.approvalSubject(eq(11L),anyBoolean(),anyBoolean())).thenReturn(new Subject(entity,20L,4L,Map.of("quantity",2)));
        var attempt=service.list(entity).getFirst();assertEquals(3L,attempt.submittedVersion());assertFalse(attempt.currentContent());assertEquals("APPROVED",attempt.status());
    }
    @Test void foreignTenantOrAttemptIsNotReadable(){
        service.start(entity,request("first",3));
        assertThrows(BusinessContractException.class,()->service.list(new EntityRef(2L,"IMP","arrival",11L)));
        var other=new EntityRef(1L,"IMP","arrival",22L);
        when(owner.approvalSubject(eq(22L),anyBoolean(),anyBoolean())).thenReturn(new Subject(other,20L,3L,Map.of()));
        assertThrows(BusinessContractException.class,()->service.detail(other,100L));verify(processes,never()).detail(any(),any());
    }
    @Test void callbackMustMatchTheOriginalProcessAndVersion(){
        service.start(entity,request("first",3));var good=result("first","APPROVED");
        assertThrows(BusinessContractException.class,()->service.record(new Result(good.context(),good.definitionId(),"foreign","APPROVED",null)));
        assertThrows(BusinessContractException.class,()->service.record(new Result(new Context(entity,20L,4L,"first",9L),good.definitionId(),good.instanceId(),"APPROVED",null)));
        assertEquals("PENDING",rows.get(100L).getStatus());verifyNoInteractions(events);
    }
    @Test void revokedBusinessPermissionPreventsProcessDiscoveryAndSubmission(){
        when(owner.approvalSubject(eq(11L),eq(true),anyBoolean())).thenThrow(new BusinessContractException("ACCESS_DENIED","denied"));
        assertThrows(BusinessContractException.class,()->service.definitions(entity));
        assertThrows(BusinessContractException.class,()->service.start(entity,request("first",3)));verifyNoInteractions(processes);
    }
}
