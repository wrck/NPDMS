package cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SrvTaskMapperContractTest {

    @Test
    void governanceQueryMustKeepTenantDeletionCandidateAndStableOrder() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/srvtask/SrvTaskMapper.xml"));
        assertTrue(xml.contains("tenant_id = #{query.tenantId}"));
        assertTrue(xml.contains("deleted = b'0'"));
        assertTrue(xml.contains("collection=\"query.projectIds\""));
        assertTrue(xml.contains("ORDER BY project_id, id"));
    }
}
