package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.FinalCustomerContactMigrationApi.*;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ProjectCustomerContactDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.FinalCustomerContactMigrationMapper;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FinalCustomerContactMigrationServiceTest {
    private final ProjectContactContextApi projects=mock(ProjectContactContextApi.class);
    private final FinalCustomerContactMigrationMapper mapper=mock(FinalCustomerContactMigrationMapper.class);
    private final FinalCustomerContactMigrationService owner=new FinalCustomerContactMigrationService(projects,mapper);
    private final Query query=new Query(1L,1L,7L,2L);
    private final List<ProjectCustomerContactDO> rows=new ArrayList<>();
    private final LocalDateTime original=LocalDateTime.of(2023,4,14,14,55,2);
    private HistoricalContact source(){return new HistoricalContact("256075","51922","10","60","","董颖","13500802833","","2",original,"22",null,null,original,null);}
    private Command command(HistoricalContact source){return new Command(query,99L,owner.preview(query).existingContactsChecksum(),List.of(source));}
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        when(projects.lockHistoricalFinalCustomer(any())).thenReturn(new ProjectContactContextApi.FinalCustomer(99L,7L,"最终用户","51922"));
        when(mapper.selectProjectForUpdate(any())).thenAnswer(i->new ArrayList<>(rows));
        when(mapper.insertHistorical(any())).thenAnswer(i->{rows.add(i.getArgument(0,FinalCustomerContactMigrationMapper.HistoricalInsert.class).values());return 1;});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void previewValidatesTheSameSourceRulesWithoutInserting(){
        owner.validateContacts(command(source()));assertTrue(rows.isEmpty());
        var missing=new HistoricalContact("256075","51922","10","60","","董颖","13500802833","","2",null,"22",null,null,original,null);
        assertThrows(IllegalArgumentException.class,()->owner.validateContacts(command(missing)));
        verify(mapper,never()).insertHistorical(any());
    }
    @Test void carriesOriginalContactWithoutBuyerMasterPrimaryOrAuditFabrication(){
        var result=owner.carry(command(source()));var row=rows.getFirst();
        assertNull(row.getCustomerId());assertNull(row.getCustomerContactId());assertEquals(99L,row.getProjectPartyId());
        assertEquals("",row.getContactCode());assertEquals("",row.getEmail());assertEquals("13500802833",row.getPhone());assertNull(row.getMobile());
        assertFalse(row.getPrimaryFlag());assertNull(row.getPrimarySetTime());assertEquals(original,row.getCreateTime());assertEquals(original,row.getUpdateTime());
        assertNull(row.getUpdater());assertEquals("22",row.getCreator());assertEquals(original,row.getEffectiveFrom());assertNull(row.getEffectiveTo());
        owner.verify(query,result);assertEquals(1,rows.size());
    }
    @Test void preservesAllExistingContacts(){
        var existing=new ProjectCustomerContactDO();existing.setId(3L);existing.setName("原主联系人");existing.setPrimaryFlag(true);existing.setVersion(12);rows.add(existing);
        owner.carry(command(source()));assertSame(existing,rows.getFirst());assertTrue(existing.getPrimaryFlag());assertEquals(12,existing.getVersion());
    }
    @Test void rejectsStalePreviewBeforeInsert(){
        var request=command(source());var extra=new ProjectCustomerContactDO();extra.setId(42L);rows.add(extra);
        assertThrows(IllegalArgumentException.class,()->owner.carry(request));verify(mapper,never()).insertHistorical(any());
    }
    @Test void rejectsUnverifiedPartyAndCrossTenant(){
        var request=command(source());assertThrows(IllegalArgumentException.class,()->owner.carry(new Command(query,100L,request.existingContactsChecksum(),request.contacts())));
        assertThrows(IllegalArgumentException.class,()->owner.preview(new Query(2L,1L,7L,2L)));verify(mapper,never()).insertHistorical(any());
    }
    @Test void rejectsWrongOriginalProjectAndNonFinalCustomerRole(){
        var wrongProject=new HistoricalContact("256075","50000","10","60","","董颖","13500802833","","2",original,"22",null,null,original,null);
        assertThrows(IllegalArgumentException.class,()->owner.carry(command(wrongProject)));
        var wrongRole=new HistoricalContact("256075","51922","10","30","","董颖","13500802833","","2",original,"22",null,null,original,null);
        assertThrows(IllegalArgumentException.class,()->owner.carry(command(wrongRole)));verify(mapper,never()).insertHistorical(any());
    }
    @Test void refusesMissingHistoricalCreationDate(){
        var missing=new HistoricalContact("256075","51922","10","60","","董颖","13500802833","","2",null,"22",null,null,original,null);
        assertThrows(IllegalArgumentException.class,()->owner.carry(command(missing)));verify(mapper,never()).insertHistorical(any());
    }
    @Test void replayProtectsEveryFormalField(){
        var result=owner.carry(command(source()));rows.getFirst().setRemark("后续人工修改");
        assertThrows(IllegalArgumentException.class,()->owner.verify(query,result));assertEquals("后续人工修改",rows.getFirst().getRemark());
    }
    @Test void catchesUnexpectedAuditMutationDuringInsert(){
        doAnswer(i->{var row=i.getArgument(0,FinalCustomerContactMigrationMapper.HistoricalInsert.class).values();row.setUpdater("1");rows.add(row);return 1;}).when(mapper).insertHistorical(any());
        assertThrows(IllegalArgumentException.class,()->owner.carry(command(source())));
    }
}
