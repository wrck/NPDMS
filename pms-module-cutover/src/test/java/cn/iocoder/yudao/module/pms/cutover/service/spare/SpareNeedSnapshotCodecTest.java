package cn.iocoder.yudao.module.pms.cutover.service.spare;

import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.AssessmentNeedSource;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.ChecklistRiskNeedSource;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort.FileExpectation;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort.FileFact;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort.FileFactVersion;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.SpareApplicationGateway.SpareDeviceContext;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.SpareApplicationGateway.SpareInitiationCommand;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.SpareApplicationGateway.SpareInitiationProviderResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpareNeedSnapshotCodecTest {

    private final SpareNeedSnapshotCodec codec = new SpareNeedSnapshotCodec();

    @Test
    void roundTripsTheStableDiscriminatedNeedSnapshot() {
        SpareNeedSnapshot snapshot = new SpareNeedSnapshot(true, List.of(
                new ChecklistRiskNeedSource(9_007_199_254_740_992L, 0,
                        "MAJOR_PROJECT_SPARES", true),
                new AssessmentNeedSource(11L, 3, true)));

        String json = codec.encode(snapshot);
        SpareNeedSnapshot decoded = codec.decode(json);

        assertThat(decoded).isEqualTo(snapshot);
        assertThat(decoded.sources()).extracting(SpareNeedSnapshot.NeedSource::sourceType)
                .containsExactly("ASSESSMENT", "CHECKLIST_RISK");
        assertThat(json).contains("\"sourceId\":\"9007199254740992\"")
                .contains("\"sourceVersion\":0")
                .doesNotContain("resultId");
    }

    @Test
    void roundTripsTheNoCurrentNeedProjection() {
        SpareNeedSnapshot snapshot = new SpareNeedSnapshot(false, List.of());

        assertThat(codec.decode(codec.encode(snapshot))).isEqualTo(snapshot);
    }

    @Test
    void carriesFrozenDeviceAndFileFactsWithoutProvidingAnImplementation() {
        SpareNeedSnapshot need = new SpareNeedSnapshot(true,
                List.of(new AssessmentNeedSource(11L, 3, true)));
        SpareInitiationCommand command = new SpareInitiationCommand(1L, "request-1", 20L, "CUT-20", 4,
                30L, List.of(new SpareDeviceContext(42L, "SN-42", 7L),
                new SpareDeviceContext(41L, "SN-41", 6L)), need, "correlation-1");
        SpareInitiationProviderResult result = new SpareInitiationProviderResult(
                "SPARE_SYSTEM", "external-request-1", null,
                "https://spare.example/launch/request-1", null);
        FileFactVersion fileVersion = new FileFactVersion(2, 3, 4);
        FileExpectation expected = new FileExpectation(1L, 5L, 30L, 20L,
                50L, "reference-50", 2, fileVersion, 8L);
        FileFact fact = new FileFact(50L, "reference-50", 2, fileVersion, 8L, "备件协同证据.pdf");

        assertThat(command.devices()).extracting(SpareDeviceContext::deviceId).containsExactly(41L, 42L);
        assertThat(result.externalApplicationNo()).isNull();
        assertThat(result.launchUrl()).startsWith("https://");
        assertThat(fact.artifactId()).isEqualTo(expected.artifactId());
        assertThat(fact.fileFactVersion()).isEqualTo(expected.fileFactVersion());
        assertThat(fact.displayName()).isEqualTo("备件协同证据.pdf");
    }
}
