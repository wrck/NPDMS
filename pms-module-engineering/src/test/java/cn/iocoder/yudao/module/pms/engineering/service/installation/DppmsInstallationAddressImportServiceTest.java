package cn.iocoder.yudao.module.pms.engineering.service.installation;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** Withdrawn duplicate-entity path cannot persist any new history. */
class DppmsInstallationAddressImportServiceTest {
    DppmsInstallationAddressImportService service=new DppmsInstallationAddressImportService();
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void withdrawnCandidateImporterIsBlockedAndForeignTenantStillRejected() {
        TenantContextHolder.setTenantId(1L);
        var address=new Address("301",500L,"地址",null,"10","SN",301L,10L);
        assertThrows(IllegalArgumentException.class,()->service.importAddresses(new Command(1L,1L,100L,List.of(address),"v",null)));
        assertThrows(IllegalArgumentException.class,()->service.importAddresses(new Command(2L,1L,100L,List.of(),"v",null)));
        assertTrue(service.importAddresses(new Command(1L,1L,100L,List.of(),"v",null)).isEmpty());
    }
}
