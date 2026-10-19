package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReviewedDirectConfigurationTest {
    record Case(Class<? extends DataSyncAdapter> adapter,SyncDefinition definition) {}
    List<Case> cases(){return List.of(
        new Case(DppmsDirectRealProductTask.class,DppmsDirectRealProductTemplate.create(9L)),
        new Case(DppmsDirectDeviceBaseTask.class,DppmsDirectDeviceBaseTemplate.create(9L)),
        new Case(DppmsDirectMemberHistoryTask.class,DppmsDirectMemberHistoryTemplate.create(9L)),
        new Case(DppmsDirectInstallationTask.class,DppmsDirectInstallationTemplate.create(9L)),
        new Case(DppmsDirectFactoryHistoryTask.class,DppmsDirectFactoryHistoryTemplate.create(9L)),
        new Case(DppmsDirectSoftwareHistoryTask.class,DppmsDirectSoftwareHistoryTemplate.create(9L)),
        new Case(DppmsDirectLicenseHistoryTask.class,DppmsDirectLicenseHistoryTemplate.create(9L)));}
    DataSyncAdapter adapter(Case c) throws Exception {
        var constructor=c.adapter().getConstructors()[0];var parameters=constructor.getParameters();var args=new Object[parameters.length];
        for(int i=0;i<args.length;i++)if(parameters[i].getType()==String.class)args[i]=parameters[i].getName().equals("keyId")?"V1":"";
        return (DataSyncAdapter)constructor.newInstance(args);
    }
    @Test void everyFrozenDefinitionPassesRealValidatorWithItsActualDescriptor() throws Exception {
        for(var c:cases())assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter(c))).validate(c.definition()),c.definition().adapter());
    }
    @Test void directMemberProjectionStillRoutesRole60AndRejectsDuplicateNativeIds() {
        var d=DppmsDirectMemberHistoryTemplate.create(9L);var rows=List.<Map<String,Object>>of(Map.of("id","1","memberRole","30","originalProjectType","10"),Map.of("id","2","memberRole","60","originalProjectType","10"));
        var input=new SyncSourceProjection.Input(d,d.sources().getFirst(),rows);var result=SyncSourceProjection.expand(input);assertEquals("1",result.get(0).rows().getFirst().get("id"));assertEquals("2",result.get(1).rows().getFirst().get("id"));
        assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.expand(new SyncSourceProjection.Input(d,d.sources().getFirst(),List.of(rows.getFirst(),rows.getFirst()))));
    }
    @Test void directInstallationSourceCannotDropAnyFrozenNativeColumns() {
        var d=DppmsDirectInstallationTemplate.create(9L);var source=d.sources().getFirst().toBuilder().columns(List.of("id")).build();
        assertThrows(IllegalArgumentException.class,()->DppmsInstallationStream.validate(d.toBuilder().sources(List.of(source)).build()));
    }
}
