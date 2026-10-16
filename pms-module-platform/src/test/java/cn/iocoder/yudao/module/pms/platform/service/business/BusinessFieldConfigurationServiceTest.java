package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.view.BusinessModelViews.ModelDetailVO;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessconfiguration.BusinessFieldConfigurationDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessconfiguration.BusinessFieldConfigurationMapper;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessFieldConfigurationServiceTest {
    final BusinessFieldConfigurationMapper mapper=mock(BusinessFieldConfigurationMapper.class);
    final DirectBusinessOwners owners=mock(DirectBusinessOwners.class);
    final BusinessCallerContext callers=mock(BusinessCallerContext.class);
    final ProjectBusinessService<?> owner=mock(ProjectBusinessService.class);
    final BusinessFieldConfigurationService service=new BusinessFieldConfigurationService(mapper,owners,callers,mock(OperationAuditApi.class));
    final Identity identity=new Identity("IMP","arrival");
    final EntityActor actor=new EntityActor(7L,9L,"configuration-test");
    final BusinessFieldConfigurationDO row=new BusinessFieldConfigurationDO();

    @BeforeEach void setup() {
        when(callers.require()).thenReturn(new AbstractBusinessApplicationService.ResolvedCaller(7L,9L,"configuration-test"));
        doReturn(Optional.of(owner)).when(owners).byIdentity("IMP","arrival");
        when(owner.configurationModel(anyBoolean())).thenReturn(new ModelDetailVO("IMP","arrival","IMP_ARRIVAL","到货",null,List.of(),List.of(),List.of()));
        row.setId(12L);row.setVersion(3L);row.setFieldsJson("[]");
        when(mapper.selectConfiguration(any())).thenReturn(row);
        when(mapper.updateConfiguration(any())).thenReturn(1);
    }
    @Test void oldSavedConfigurationDefaultsOffAndFeatureSettingsSurvivePersistenceAndReread() {
        assertEquals(new Features(false,false),service.read(identity,actor).features());
        var configured=new Configuration(3,List.of(),List.of(new Named("approval","审批",List.of(),null,new Features(false,true))),"approval",new Features(true,false));
        var saved=service.save(identity,actor,configured);
        var update=org.mockito.ArgumentCaptor.forClass(BusinessFieldConfigurationMapper.Save.class);
        verify(mapper).updateConfiguration(update.capture());
        var persisted=JsonUtils.parseObject(update.getValue().fieldsJson(),Configuration.class);
        assertEquals(3L,update.getValue().expectedVersion());assertEquals(configured.features(),persisted.features());
        assertEquals(configured.profiles(),persisted.profiles());row.setFieldsJson(update.getValue().fieldsJson());
        row.setVersion(4L);
        assertEquals(saved,service.read(identity,actor));
        service.save(identity,actor,4,List.of());
        verify(mapper).updateConfiguration(argThat(value->value.expectedVersion()==4
                && JsonUtils.parseObject(value.fieldsJson(),Configuration.class).features().attachments()));
    }
    @Test void configurationPermissionAndVersionConflictStillRejectChanges() {
        when(mapper.updateConfiguration(any())).thenReturn(0);
        assertThrows(BusinessContractException.class,()->service.save(identity,actor,new Configuration(3,List.of())));
        clearInvocations(mapper);
        doThrow(new BusinessContractException("ACCESS_DENIED","no configuration permission")).when(owner).configurationModel(true);
        assertThrows(BusinessContractException.class,()->service.save(identity,actor,new Configuration(3,List.of())));
        verifyNoInteractions(mapper);
    }
    @Test void readRequiresCurrentOwnerQueryPermissionWithoutBuildingWritePermissions(){
        assertEquals(3L,service.read(identity,actor).version());
        verify(owner).readableDefinition();verify(owner,never()).configurationModel(anyBoolean());
        clearInvocations(mapper);
        doThrow(new BusinessContractException("ACCESS_DENIED","read revoked")).when(owner).readableDefinition();
        assertThrows(BusinessContractException.class,()->service.read(identity,actor));
        assertThrows(BusinessContractException.class,()->service.read(identity,new EntityActor(8L,9L,"foreign tenant")));
        assertThrows(BusinessContractException.class,()->service.read(identity,new EntityActor(7L,10L,"foreign user")));
        verifyNoInteractions(mapper);
    }
}
