package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CurrentProjectDeviceIdsQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DeviceCurrentProjectIdsQueryTest {
    @Test void emptyOrMissingScopeNeverExpandsTheQuery() throws Exception {
        DeviceJoinedPageSqlTest.withDatabase(mapper -> {
            for (var query : List.of(new CurrentProjectDeviceIdsQuery(1L, 11L, Set.of()),
                    new CurrentProjectDeviceIdsQuery(1L, 11L, null), new CurrentProjectDeviceIdsQuery(1L, null, Set.of(1L)),
                    new CurrentProjectDeviceIdsQuery(null, 11L, Set.of(1L)))) {
                assertTrue(mapper.selectCurrentProjectDevicesByIds(query).isEmpty());
            }
        });
    }

    @Test void fullProjectReadUsesOnlyAuthorizedIdsAndTheCurrentProductFromItsSourceSql() throws Exception {
        DeviceJoinedPageSqlTest.withDatabase(sql -> {
            sql.execute("UPDATE ast_device SET product_code='A' WHERE id=1");
            sql.execute("INSERT INTO ast_product_official_info(tenant_id,product_code,product_model,source_system,status,deleted) VALUES(1,'A','MASTER','OTHER','INACTIVE',false)");
        }, mapper -> {
            var rows = mapper.selectCurrentProjectDevicesByIds(new CurrentProjectDeviceIdsQuery(
                    1L, 11L, Set.of(1L, 2L, 4L, 12L, 13L, 15L)));
            assertEquals(List.of(1L), rows.stream().map(row -> row.getId()).toList());
            assertEquals("MASTER", rows.getFirst().getProductModel());
            assertEquals(List.of(1L), mapper.selectCurrentProjectDevices(
                    new cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery(1L, 11L))
                    .stream().map(row -> row.getId()).toList());
        });
    }
}
