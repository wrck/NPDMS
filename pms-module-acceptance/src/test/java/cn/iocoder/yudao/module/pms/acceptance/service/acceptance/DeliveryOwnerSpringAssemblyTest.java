package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeliveryOwnerSpringAssemblyTest {
 @Configuration @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {
  @Bean PlatformTransactionManager transactionManager(){return new DataSourceTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:delivery_owner_assembly;DB_CLOSE_DELAY=-1","sa",""));}
 }
 /** Cycle members are production beans; only external ports and persistence are fixtures. */
 @Test void productionDeliveryCycleMembersAssembleWithCircularReferencesDisabledAndKeepCallerLockTransaction(){
  try(var context=new AnnotationConfigApplicationContext()){
   context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
   context.register(Transactions.class);
   var actual=List.of(DeliveryTemplateFrozenService.class,DeliveryRequirementService.class,DeliveryMaterialService.class,
     DeliveryOwnerAccess.class,ProjectDeliverableUploadPolicyValidator.class,DeliveryRequirementIdentityApiImpl.class);
   Set<Class<?>> fixtures=new HashSet<>();
   for(Class<?> type:actual){
    for(Constructor<?> constructor:type.getDeclaredConstructors())for(Class<?> port:constructor.getParameterTypes())addFixture(port,actual,fixtures);
    for(Field field:type.getDeclaredFields())if(field.isAnnotationPresent(jakarta.annotation.Resource.class)||field.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class))addFixture(field.getType(),actual,fixtures);
   }
   fixtures.remove(PlatformDeliveryRequirementApi.class);fixtures.remove(PlatformDeliveryRequirementIdentityApi.class);
   for(Class<?> fixture:fixtures)registerFixture(context,fixture);
   context.register(actual.toArray(Class<?>[]::new));context.refresh();
   for(Class<?> type:actual)assertNotNull(context.getBean(type));
   assertSame(context.getBean(DeliveryTemplateFrozenService.class),context.getBean(PlatformDeliveryRequirementApi.class));
   var mapper=context.getBean(DeliveryRequirementMapper.class);var identity=context.getBean(PlatformDeliveryRequirementIdentityApi.class);
   TenantContextHolder.setTenantId(7L);
   when(mapper.selectIdentity(any())).thenReturn(null);assertFalse(identity.containsTemplateIdentity(42L,"D1"));
   when(mapper.selectIdentityForUpdate(any())).thenAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());var q=(cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementIdentityLockQuery)call.getArgument(0);assertEquals(7L,q.tenantId());assertEquals("ACC",q.ownerModule());assertEquals("project_deliverable",q.entityType());assertEquals(42L,q.entityId());assertEquals("D1",q.typeCode());return new DeliveryRequirementDO();});
   new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status->{assertTrue(identity.lockTemplateIdentity(42L,"D1"));assertTrue(TransactionSynchronizationManager.isActualTransactionActive());});
   assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
  }finally{TenantContextHolder.clear();}
 }
 private static void addFixture(Class<?> type,List<Class<?>> actual,Set<Class<?>> fixtures){
  if(!actual.contains(type)&&type!=ObjectProvider.class&&!Collection.class.isAssignableFrom(type)&&!Map.class.isAssignableFrom(type))fixtures.add(type);
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void registerFixture(AnnotationConfigApplicationContext c,Class type){c.registerBean(type.getName(),type,()->mock(type));}
}
