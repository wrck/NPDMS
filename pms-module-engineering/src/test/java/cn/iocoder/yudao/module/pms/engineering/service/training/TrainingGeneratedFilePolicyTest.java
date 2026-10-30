package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFilePolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingGeneratedFilePolicyTest {
    final TrainingMapper mapper=mock(TrainingMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final ProjectAcceptanceContextApi projects=mock(ProjectAcceptanceContextApi.class);
    final TrainingGeneratedFilePolicy policy=new TrainingGeneratedFilePolicy(mapper,permissions,scopes,projects);
    TrainingDO row;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        row=new TrainingDO();row.setTenantId(1L);row.setId(10L);row.setProjectId(7L);row.setVersion(3L);row.setStatus(0);
        when(mapper.selectFileOwnerForUpdate(any())).thenReturn(row);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    NativeGeneratedFilePolicyQuery query(Long tenant,Long version,String purpose){
        return new NativeGeneratedFilePolicyQuery(tenant,9L,"IMP","TRAINING_RECORD",10L,version,purpose,null);
    }
    @Test void rejectsStaleNativeVersionBeforeProjectAccess(){
        assertThrows(BusinessContractException.class,()->policy.lockAndRevalidateNativeGeneratedFile(query(1L,2L,"TRAINING_RECORD_PDF/2")));
        verifyNoInteractions(scopes,projects);
    }
    @Test void rejectsVoidOwnerBeforeProjectAccess(){
        row.setStatus(3);
        assertThrows(BusinessContractException.class,()->policy.lockAndRevalidateNativeGeneratedFile(query(1L,3L,"TRAINING_RECORD_PDF/3")));
        verifyNoInteractions(scopes,projects);
    }
    @Test void rejectsCrossTenantBeforeReadingOwner(){
        assertThrows(BusinessContractException.class,()->policy.lockAndRevalidateNativeGeneratedFile(query(2L,3L,"TRAINING_RECORD_HTML/3")));
        verifyNoInteractions(mapper,permissions,scopes,projects);
    }
    @Test void rejectsGenerationWithoutNativeOperationPermission(){
        assertThrows(BusinessContractException.class,()->policy.lockAndRevalidateNativeGeneratedFile(query(1L,3L,"TRAINING_RECORD_PDF/3")));
        verifyNoInteractions(scopes,projects);
    }
}
