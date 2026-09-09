package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectCustomerContactServiceTest {
    private final ProjectContactContextApi projects = mock(ProjectContactContextApi.class);
    private final CustomerContactMasterMapper sources = mock(CustomerContactMasterMapper.class);
    private final ProjectCustomerContactMapper contacts = mock(ProjectCustomerContactMapper.class);
    private final ContactHistoryMapper history = mock(ContactHistoryMapper.class);
    private final ProjectCustomerContactService service = new ProjectCustomerContactService(projects, sources, contacts,
            history, mock(PlatformCommandExecutionApi.class), mock(CustomerQueryService.class), mock(CustomerScopeContextService.class));
    private final CustomerContactMasterService.Actor actor = new CustomerContactMasterService.Actor(1L, 3L);

    @BeforeEach void context() {
        when(projects.lockForWrite(any())).thenReturn(new ProjectContactContextApi.Context(7L, 8L, 2, "ACTIVE", true));
        var customer = new CustomerMasterDO(); customer.setId(8L); customer.setLifecycleStatus("ENABLED");
        when(sources.selectCustomerForUpdate(any())).thenReturn(customer);
    }
    private ProjectCustomerContactDO row() {
        var row = new ProjectCustomerContactDO(); row.setId(10L); row.setTenantId(1L); row.setProjectId(7L);
        row.setCustomerId(8L); row.setCustomerContactId(9L); row.setName("客户原姓名"); row.setMobile("13800138000");
        row.setVersion(2); row.setStatus(0); row.setPrimaryFlag(false); return row;
    }
    private ProjectContactWrite write(int version) {
        return new ProjectContactWrite(7L, 10L, null, 2, version,
                new ContactValues("仅本项目修改", null, null, "13800138001", null, null, null, null), false, 0, false);
    }

    @Test void projectEditNeverWritesTheCustomerMaster() {
        var existing = row(); when(contacts.selectForUpdate(any())).thenReturn(existing);
        var source = new CustomerContactMasterDO(); source.setId(9L); source.setStatus(0); source.setName("客户原姓名");
        when(sources.selectForUpdate(any())).thenReturn(source);
        when(contacts.updateById(any(ProjectCustomerContactDO.class))).thenReturn(1);
        var updated = service.update(actor, write(2));
        assertEquals("仅本项目修改", updated.getName());
        assertEquals("客户原姓名", source.getName()); assertEquals("客户原姓名", existing.getName());
        verify(sources, never()).updateById(any(CustomerContactMasterDO.class));
        verify(sources, never()).insert(any(CustomerContactMasterDO.class));
        verify(history).insert(argThat((ContactHistoryDO value) -> value.getProjectId().equals(7L) && value.getProjectRelationId().equals(10L)));
    }

    @Test void managerCanListAllStatusesWhileViewerOnlyListsEnabledContacts() {
        when(projects.inspect(any())).thenReturn(new ProjectContactContextApi.Context(7L,8L,2,"ACTIVE",true));
        when(contacts.selectPage(any())).thenReturn(PageResult.empty());
        service.page(actor, 7L, null, "查询姓名", new cn.iocoder.yudao.framework.common.pojo.PageParam());
        verify(contacts).selectPage(argThat(query -> query.status() == null && "查询姓名".equals(query.name())));
        when(projects.inspect(any())).thenReturn(new ProjectContactContextApi.Context(7L,8L,2,"ACTIVE",false));
        service.page(actor, 7L, 1, null, new cn.iocoder.yudao.framework.common.pojo.PageParam());
        verify(contacts).selectPage(argThat(query -> Integer.valueOf(0).equals(query.status())));
    }

    @Test void defaultsPreserveBothLocallyEditedAndDeletedRelations() {
        var source = new CustomerContactMasterDO(); source.setId(9L); source.setCustomerId(8L); source.setStatus(0);
        when(sources.selectPage(any())).thenReturn(new PageResult<>(List.of(source), 1L));
        var existing = row(); existing.setDeleted(true);
        when(contacts.selectSourceIncludingDeletedForUpdate(any())).thenReturn(existing);
        assertEquals(0, service.importDefaults(actor, 7L, 2));
        verify(contacts, never()).insert(any(ProjectCustomerContactDO.class));
        verify(contacts, never()).updateById(any(ProjectCustomerContactDO.class));
        verifyNoInteractions(history);
    }

    @Test void staleProjectContactVersionCannotOverwriteOrAppendHistory() {
        when(contacts.selectForUpdate(any())).thenReturn(row());
        assertThrows(ServiceException.class, () -> service.update(actor, write(1)));
        verify(contacts, never()).updateById(any(ProjectCustomerContactDO.class));
        verifyNoInteractions(history);
    }

    @Test void mainContactDeletionNeedsExplicitConfirmation() {
        var existing = row(); existing.setPrimaryFlag(true);
        when(contacts.selectForUpdate(any())).thenReturn(existing);
        assertThrows(ServiceException.class, () -> service.delete(actor, write(2)));
        verify(contacts, never()).deleteByVersion(any());
        when(contacts.deleteByVersion(any())).thenReturn(1);
        service.delete(actor, new ProjectContactWrite(7L,10L,null,2,2,null,false,0,true));
        verify(contacts).deleteByVersion(any());
        verify(sources, never()).deleteUnreferenced(any());
    }

    @Test void explicitRestoreRetainsLocalValuesWithoutRestoringOldPrimaryFlag() {
        var existing = row(); existing.setDeleted(true); existing.setPrimaryFlag(true); existing.setVersion(4);
        when(contacts.selectIncludingDeletedForUpdate(any())).thenReturn(existing);
        var source = new CustomerContactMasterDO(); source.setStatus(0);
        when(sources.selectForUpdate(any())).thenReturn(source);
        when(contacts.restoreByVersion(any())).thenReturn(1);
        service.restore(actor, 7L, 10L, 2, 4, 0);
        verify(history).insert(argThat((ContactHistoryDO value) -> "RESTORE".equals(value.getActionCode())
                && value.getAfterValues().contains("客户原姓名") && value.getAfterValues().contains("\"primaryFlag\":false")));
        verify(sources, never()).updateById(any(CustomerContactMasterDO.class));
        assertThrows(ServiceException.class, () -> service.restore(actor, 7L, 10L, 2, 3, 0));
    }

    @Test void disabledContactWithoutChannelsCanBeRestoredDisabledUnderDisabledCustomer() {
        var customer = new CustomerMasterDO(); customer.setId(8L); customer.setLifecycleStatus("DISABLED");
        when(sources.selectCustomerForUpdate(any())).thenReturn(customer);
        var existing = row(); existing.setDeleted(true); existing.setStatus(1); existing.setMobile(null); existing.setVersion(4);
        when(contacts.selectIncludingDeletedForUpdate(any())).thenReturn(existing);
        when(contacts.restoreByVersion(any())).thenReturn(1);
        service.restore(actor, 7L, 10L, 2, 4, 1);
        verify(contacts).restoreByVersion(argThat(command -> command.status() == 1));
        assertThrows(ServiceException.class, () -> service.restore(actor, 7L, 10L, 2, 4, 0));
        when(contacts.selectForUpdate(any())).thenReturn(existing);
        when(contacts.deleteByVersion(any())).thenReturn(1);
        service.delete(actor, new ProjectContactWrite(7L,10L,null,2,4,null,false,1,false));
        verify(contacts).deleteByVersion(any());
    }
}
