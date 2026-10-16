package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.service.business.DirectBusinessOwners;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultBusinessDeliveryListingTest {
    final BusinessModelCatalog catalog=mock(BusinessModelCatalog.class);
    final DirectBusinessOwners direct=mock(DirectBusinessOwners.class);
    final BusinessCallerContext callers=mock(BusinessCallerContext.class);
    final ProjectScopeApi projects=mock(ProjectScopeApi.class);
    final DeliveryMaterialMapper materials=mock(DeliveryMaterialMapper.class);
    final BusinessModelDescriptor model=mock(BusinessModelDescriptor.class);
    final DefaultBusinessDeliveryService service=new DefaultBusinessDeliveryService(catalog,mock(BusinessAccessGuard.class),
            callers,mock(DeclaredBusinessDeliveryBridge.class),projects,mock(FileUploadApplicationService.class),
            mock(FileEvidenceApi.class),materials,mock(DeliveryCatalogService.class));

    @BeforeEach void setup() {
        when(callers.require()).thenReturn(new AbstractBusinessApplicationService.ResolvedCaller(7L,9L,null));
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(99L,1L,Set.of(99L),Set.of()));
        when(model.ownerModule()).thenReturn("IMP");when(model.entityType()).thenReturn("arrival");when(model.stableCode()).thenReturn("IMP_ARRIVAL");
        var owner=mock(ProjectBusinessService.class);when(owner.definition()).thenReturn(model);
        doReturn(Optional.of(owner)).when(direct).byCode("IMP_ARRIVAL");
        when(direct.definitions()).thenReturn(List.of(model));
        when(direct.require(7L,9L,"IMP","arrival",42L,false,false,null)).thenReturn(new DirectBusinessOwners.Access(99L,1L));
        @SuppressWarnings("unchecked") ObjectProvider<DirectBusinessOwners> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(direct);ReflectionTestUtils.setField(service,"directOwners",provider);
        when(materials.selectDefaultDeliveryPage(any())).thenReturn(new PageResult<>(List.of(),0L));
    }

    @Test void entityListChecksOnlyItsOwnModelAndKeepsTheExactScope() {
        assertEquals(0L,service.list(99L,"ATTACHMENT","IMP_ARRIVAL","42",1,20).getTotal());
        verify(catalog,never()).all();verify(direct,never()).definitions();
        verify(direct).requireReadableModel("IMP_ARRIVAL");
        verify(direct).require(7L,9L,"IMP","arrival",42L,false,false,null);
        verify(materials).selectDefaultDeliveryPage(argThat(query->query.getTenantId().equals(7L)
                && query.getProjectId().equals(99L) && query.getEntityId().equals(42L)
                && query.getReadableBusinessTypes().equals(Set.of("IMP_ARRIVAL"))));
    }
    @Test void deniedEntityOrProjectNeverReachesTheMaterialQuery() {
        doThrow(new BusinessContractException("ACCESS_DENIED","private entity")).when(direct).require(7L,9L,"IMP","arrival",42L,false,false,null);
        assertThrows(BusinessContractException.class,()->service.list(99L,"ATTACHMENT","IMP_ARRIVAL","42",1,20));
        verifyNoInteractions(materials);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(99L,1L,Set.of(),Set.of()));
        assertThrows(BusinessContractException.class,()->service.list(99L,"ATTACHMENT","IMP_ARRIVAL","42",1,20));
        verifyNoInteractions(materials);
    }
    @Test void modelDenialStillReturnsNoRowsAndDoesNotExpandTheQuery() {
        doThrow(new BusinessContractException("ACCESS_DENIED","private model")).when(direct).requireReadableModel("IMP_ARRIVAL");
        assertEquals(0L,service.list(99L,"ATTACHMENT","IMP_ARRIVAL","42",1,20).getTotal());
        verifyNoInteractions(materials);
    }
    @Test void projectCollectionStillChecksAllBusinessModels() {
        when(materials.selectDefaultDeliveryList(any())).thenReturn(List.of());
        assertEquals(0L,service.list(99L,"ATTACHMENT",null,null,1,20).getTotal());
        verify(catalog).all();verify(direct).definitions();verify(direct).requireReadableModel("IMP_ARRIVAL");
    }
}
