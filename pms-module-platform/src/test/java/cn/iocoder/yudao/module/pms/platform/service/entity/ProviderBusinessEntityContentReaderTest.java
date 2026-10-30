package cn.iocoder.yudao.module.pms.platform.service.entity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderBusinessEntityContentReaderTest {
    final BusinessEntityIdentityResolver identities=mock(BusinessEntityIdentityResolver.class);
    final EntityProviderRegistry registry=mock(EntityProviderRegistry.class);
    final BusinessModelCatalog catalog=mock(BusinessModelCatalog.class);
    final EntityExtensionApi extensions=mock(EntityExtensionApi.class);
    final EntityFieldProvider provider=mock(EntityFieldProvider.class);
    final EntityActor actor=new EntityActor(1L,2L,null);
    final EntityDataRef exposed=new EntityDataRef(new EntityRef(1L,"SOL","requirementAnalysis",3L),4L);
    final EntityDataRef nativeTarget=new EntityDataRef(new EntityRef(1L,"SOL","REQUIREMENT_ANALYSIS",3L),4L);
    ProviderBusinessEntityContentReader reader() {
        when(registry.nativeRef(exposed)).thenReturn(nativeTarget);
        when(registry.fields(nativeTarget.entity())).thenReturn(provider);
        return new ProviderBusinessEntityContentReader(identities,registry,catalog,extensions);
    }
    @Test void ownerPermissionOrRevisionMembershipDenialPrecedesAnyContentRead() {
        var reader=reader();
        var denial=new IllegalArgumentException("revision does not belong to authorized entity");
        doThrow(denial).when(registry).requireReadable(nativeTarget,actor);
        assertSame(denial,assertThrows(IllegalArgumentException.class,()->reader.read(exposed,actor)));
        verify(registry).requireReadable(nativeTarget,actor);
        verify(provider,never()).read(any(),any());
        verifyNoInteractions(catalog,extensions);
    }
    @Test void publicReadRetainsCatalogTargetAndNativeBasisAndPreservesFalseZeroArrays() {
        var reader=reader();
        var fields=List.of(new BusinessFieldDescriptor("enabled","enabled",EntityField.Type.BOOLEAN,false,true,true,null),
                new BusinessFieldDescriptor("count","count",EntityField.Type.NUMBER,false,true,true,null),
                new BusinessFieldDescriptor("hidden","hidden",EntityField.Type.TEXT,false,false,false,null));
        when(catalog.require("SOL","requirementAnalysis")).thenReturn(new BusinessModelDescriptor("SOL","requirementAnalysis","RA",1,null,"RA",null,fields,List.of(),List.of(),List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DYNAMIC_FORM,null,true)),"sol_requirement_analysis"));
        when(provider.read(nativeTarget,actor)).thenReturn(Map.of("enabled",EntityFieldValue.known(false),"count",EntityFieldValue.known(0),"hidden",EntityFieldValue.known("secret"),"internal",EntityFieldValue.known("secret")));
        when(provider.concurrencyBasis(nativeTarget,actor)).thenReturn(7L);
        when(extensions.read(nativeTarget,actor)).thenReturn(new EntityExtensionApi.Values(5L,Map.of("enabled",true,"items",List.of("a","b")),2));
        var result=reader.read(exposed,actor);
        assertEquals(exposed.entity(),result.ref());
        assertEquals(exposed.revisionId(),result.revisionId());
        assertEquals(7L,result.concurrencyBasis());
        assertEquals(Map.of("enabled",false,"count",0,"items",List.of("a","b")),result.fieldValues());
    }
}
