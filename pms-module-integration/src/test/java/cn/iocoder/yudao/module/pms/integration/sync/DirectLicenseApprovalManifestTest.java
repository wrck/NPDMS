package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;

class DirectLicenseApprovalManifestTest {
    @Test void compressedAndPlainFactsRetainNullsAndStrictIdentityValidation() {
        var facts=java.util.stream.IntStream.rangeClosed(1,401).mapToObj(i->new Approved(""+i,null,"  MODEL  ",null,"原分类","CODE","RAW")).toList();
        var source=DppmsDirectLicenseHistoryTemplate.approvedSource(new Request(7L,null,facts));
        assertTrue(source.parameters().get("approvedLicenseFacts").toString().startsWith("gzip:"));
        var manifest=DirectLicenseApprovalManifest.load(source);assertEquals(facts,manifest.facts());
        assertSame(manifest,DirectLicenseApprovalManifest.load(source));assertNull(manifest.byId().get("1").sn());
        var parameters=new HashMap<>(source.parameters());parameters.put("approvedLicenseFacts",tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(facts));
        assertEquals(facts,DirectLicenseApprovalManifest.load(source.toBuilder().parameters(parameters).build()).facts());
        parameters.put("approvedLicenseFacts","gzip:corrupt-data");
        var error=assertThrows(IllegalArgumentException.class,()->DirectLicenseApprovalManifest.load(source.toBuilder().parameters(parameters).build()));
        assertEquals("LICENSE_APPROVAL_INVALID",error.getMessage());assertNull(error.getCause());
    }
    @Test void compressedFactsCannotSmuggleSecretsOrDuplicateIdentity() {
        var s=create(new Request(7L,10L,List.of())).sources().getFirst();var p=new HashMap<>(s.parameters());
        p.put("approvedLicenseFacts","[{\"id\":\"1\",\"licenseCode\":\"SYNTHETIC-SECRET\"}]");
        var error=assertThrows(IllegalArgumentException.class,()->DirectLicenseApprovalManifest.load(s.toBuilder().parameters(p).build()));
        assertFalse(error.toString().contains("SYNTHETIC-SECRET"));assertNull(error.getCause());
        var duplicate=new Approved("1",null,null,null,null,null,null);
        p.put("approvedLicenseFacts",DirectLicenseApprovalManifest.encode(List.of(duplicate,duplicate)));
        assertThrows(IllegalArgumentException.class,()->DirectLicenseApprovalManifest.load(s.toBuilder().parameters(p).build()));
        p.put("approvedLicenseFacts","[] []");
        assertThrows(IllegalArgumentException.class,()->DirectLicenseApprovalManifest.load(s.toBuilder().parameters(p).build()));
    }
    @Test @EnabledIfEnvironmentVariable(named="P37_APPROVAL_MANIFEST_TEST",matches="true")
    void actualWholeSourceNonSecretManifestRoundTripsWithoutSnapshotRowCap()throws Exception {
        var root=Path.of(System.getenv("P37_APPROVAL_MANIFEST_ROOT"));
        var metadata=JsonUtils.parseMap(Files.readString(root.resolve("approved-facts-manifest.json")));
        var ids=Files.readString(root.resolve("approved-ids.txt"));
        var d=DppmsDirectLicenseHistoryTemplate.create(7L);var source=d.sources().getFirst();
        source.parameters(Map.of("approvedLicenseFacts","gzip:"+Base64.getEncoder().encodeToString(Files.readAllBytes(root.resolve("approved-facts.json.gz"))),
                "approvedLicenseIds",ids,"ownerProjectId",""));
        var roundTrip=JsonUtils.parseObject(JsonUtils.toJsonString(d),SyncDefinition.class);
        var manifest=DirectLicenseApprovalManifest.load(roundTrip.sources().getFirst());
        assertEquals(((Number)metadata.get("rowCount")).intValue(),manifest.facts().size());
        assertEquals(ids,manifest.ids());assertEquals(475468,manifest.byId().size());
        assertDoesNotThrow(()->DirectSecureLicenseStream.validate(roundTrip,roundTrip.sources().getFirst()));
        assertEquals(0,((Number)metadata.get("licenseValuesRead")).intValue());
    }
}
