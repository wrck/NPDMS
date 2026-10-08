package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceApi.ProjectCommerceSourceResolveCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectCreationSourceLockTest {
    @Mock OrganizationScopeApi organizationScopeApi;
    @Mock ContractMapper contractMapper;
    @Mock ProjectContractRelationMapper relationMapper;
    @Mock SalesOrderMapper orderMapper;
    @Mock CrmExecutionOrderMapper executionOrderMapper;
    @Mock OperationAuditApi operationAuditApi;
    @Mock ContractAccessService accessService;
    @InjectMocks ProjectCommerceSourceServiceImpl service;
    ContractDO contract;
    SalesOrderDO order;
    CrmExecutionOrderDO execution;

    @BeforeEach void source() {
        contract = new ContractDO(); contract.setId(1L); contract.setCompanyCode("C1"); contract.setContractNo("CT1"); contract.setStatus("ENABLED");
        order = new SalesOrderDO(); order.setId(2L); order.setOrderNo("SO1"); order.setCompanyCode("C1"); order.setExecutionNo("EX1");
        execution = new CrmExecutionOrderDO(); execution.setId(3L); execution.setExecutionNo("EX1"); execution.setProjectName("CRM项目"); execution.setCompanyCode("C1"); execution.setSourceSystem("CRM");
        var scope = new UserCompanyDepartmentScopeRespDTO(); scope.setId(9L); scope.setVersion(1); scope.setCompanyCode("C1");
        lenient().when(contractMapper.selectByIdForUpdate(any())).thenReturn(contract);
        lenient().when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of(scope));
        lenient().when(orderMapper.selectCreationOrdersForUpdate(any())).thenReturn(List.of(order));
        lenient().when(executionOrderMapper.selectActiveForUpdate(any())).thenReturn(List.of(execution));
    }
    @Test void bindingRejectsForeignExecutionBeforeAnyWrite() {
        execution.setCompanyCode("OTHER");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.bindProjectCommerceSource(
                new cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceBindCommand(
                        1L,77L,1L,7L,"synthetic-bind",2L,fingerprint())));
        verify(relationMapper,never()).insert(any(cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO.class));
        verify(executionOrderMapper,never()).updatePrimaryProjectIfUnbound(any());
    }
    @Test void bindingMustRejectStaleFingerprintBeforeAnyWrite() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.bindProjectCommerceSource(
                new cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceBindCommand(
                        1L,77L,1L,7L,"synthetic-bind",2L,"0".repeat(64))));
        verify(relationMapper,never()).insert(any(cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO.class));
        verify(executionOrderMapper,never()).updatePrimaryProjectIfUnbound(any());
    }
    String fingerprint() { return CreationSourceResolver.fingerprint(new ContractAccessService.CreationSourceDetail(contract, List.of(order), List.of(execution), List.of())); }
    ProjectCommerceSourceResolveCommand command(String fingerprint) { return new ProjectCommerceSourceResolveCommand(1L,1L,7L,2L,fingerprint); }

    @Test void reloadsLockedHeadersAndReturnsTheCorrelatedSource() {
        var result = service.resolveCreationSourceForUpdate(command(fingerprint()));
        assertEquals("CRM项目",result.projectName());
        assertEquals(2L,result.orderFacts().id());
        assertEquals(3L,result.executionOrderId());
        verify(orderMapper).selectCreationOrdersForUpdate(any());
        verify(executionOrderMapper).selectActiveForUpdate(any());
        verifyNoInteractions(relationMapper,operationAuditApi);
    }
    @Test void changedSourceRejectsBeforeAnyBindingOrAudit() {
        var command = command(fingerprint()); execution.setProjectName("变更后的项目");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.resolveCreationSourceForUpdate(command));
        verifyNoInteractions(relationMapper,operationAuditApi);
    }
    @Test void cannotInjectAnOrderFromAnotherContract() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.resolveCreationSourceForUpdate(new ProjectCommerceSourceResolveCommand(1L,1L,7L,999L,fingerprint())));
    }
    @Test void disabledContractAndMissingCompanyScopeFailClosed() {
        contract.setStatus("DISABLED");
        assertThrows(RuntimeException.class,()->service.resolveCreationSourceForUpdate(command(fingerprint())));
        contract.setStatus("ENABLED"); when(organizationScopeApi.getActiveScopes(7L)).thenReturn(List.of());
        assertThrows(RuntimeException.class,()->service.resolveCreationSourceForUpdate(command(fingerprint())));
        verify(orderMapper,never()).selectCreationOrdersForUpdate(any());
    }
    @Test void crossCompanyRelationshipIsRejectedEvenWithMatchingDigest() {
        order.setCompanyCode("OTHER");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.resolveCreationSourceForUpdate(command(fingerprint())));
    }

    @Test void duplicateContractCannotCreateAnotherProject() {
        var relation = new cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO();
        relation.setId(8L); relation.setProjectId(78L);
        when(relationMapper.selectCurrentByContract(any())).thenReturn(List.of(relation));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () ->
                service.bindProjectCommerceSource(new cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceBindCommand(
                        1L,77L,1L,7L,"synthetic-bind",2L,fingerprint())));
        verify(relationMapper,never()).insert(any(cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO.class));
        verify(executionOrderMapper,never()).updatePrimaryProjectIfUnbound(any());
    }

    @Test void changedRelationEvidenceRejectsEvenWhenOrderSetIsUnchanged() {
        var relation = new cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO();
        relation.setId(91L); relation.setOrderId(2L); relation.setContractId(1L); relation.setSourceVersion("v1");
        when(orderMapper.selectCreationRelationsForUpdate(any())).thenReturn(List.of(relation));
        var before = CreationSourceResolver.fingerprint(new ContractAccessService.CreationSourceDetail(
                contract, List.of(order), List.of(execution), List.of(), List.of(relation)));
        relation.setSourceVersion("v2");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> service.resolveCreationSourceForUpdate(command(before)));
        verifyNoInteractions(relationMapper, operationAuditApi);
    }

    @Test void bindingClaimsOnlyTheSelectedOrdersExecution() {
        var anotherOrder = new SalesOrderDO(); anotherOrder.setId(22L); anotherOrder.setExecutionNo("EX-OTHER"); anotherOrder.setCompanyCode("C1");
        var anotherExecution = new CrmExecutionOrderDO(); anotherExecution.setId(33L); anotherExecution.setExecutionNo("EX-OTHER"); anotherExecution.setCompanyCode("C1"); anotherExecution.setSourceSystem("CRM");
        var relation = new cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO();
        relation.setId(8L); relation.setProjectId(77L);
        when(relationMapper.selectCurrentByContract(any())).thenReturn(List.of(relation));
        when(relationMapper.selectByIdentityForUpdate(any())).thenReturn(relation);
        when(orderMapper.selectCreationOrdersForUpdate(any())).thenReturn(List.of(order,anotherOrder));
        when(executionOrderMapper.selectActiveForUpdate(any())).thenReturn(List.of(execution,anotherExecution));
        when(executionOrderMapper.updatePrimaryProjectIfUnbound(any())).thenReturn(1);
        var sourceFingerprint = CreationSourceResolver.fingerprint(new ContractAccessService.CreationSourceDetail(
                contract, List.of(order, anotherOrder), List.of(execution, anotherExecution), List.of()));
        service.bindProjectCommerceSource(new cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectCommerceSourceBindCommand(
                1L,77L,1L,7L,"synthetic-bind",2L,sourceFingerprint));
        verify(executionOrderMapper).updatePrimaryProjectIfUnbound(new cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionPrimaryProjectUpdate(
                1L,3L,77L,7L,"C1",null,"EX1","CRM"));
        verify(executionOrderMapper,never()).updatePrimaryProjectIfUnbound(new cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionPrimaryProjectUpdate(
                1L,33L,77L,7L,"C1",null,"EX-OTHER","CRM"));
    }
}
