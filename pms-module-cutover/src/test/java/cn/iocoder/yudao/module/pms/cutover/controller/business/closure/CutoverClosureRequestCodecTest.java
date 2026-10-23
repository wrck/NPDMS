package cn.iocoder.yudao.module.pms.cutover.controller.business.closure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverClosureRequestCodecTest {
    private final CutoverClosureRequestCodec codec = new CutoverClosureRequestCodec();

    @Test
    void parsesStrictPositiveClosureRequestsWithoutPersistingSecretInContent() {
        var body = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree("""
                {"preCheckNormal":true,"preCheckDetail":null,"executionNormal":true,"executionDetail":null,
                 "testNormal":true,"testDetail":null,"rollbackOccurred":false,"rollbackSuccessful":null,
                 "rollbackReason":null,"legacyItems":"无","finalResult":null,"attachments":[
                 {"purposeCode":"POST_COLLECTION_CHECKLIST","artifactId":"9007199254740992","versionNo":1,
                  "referenceKey":"ref-check","fileFactVersion":{"artifactVersion":1,"referenceVersion":2,
                  "availabilityVersion":3},"scopeVersion":4,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}
                """);
        var content = codec.content(body);
        assertThat(content.attachments()).hasSize(1);
        assertThat(content.attachments().getFirst().purposeCode()).isEqualTo(AttachmentPurpose.POST_COLLECTION_CHECKLIST);
        assertThat(content.attachments().getFirst().artifactId()).isEqualTo(9_007_199_254_740_992L);

        body.put("transientSecret", "test-secret");
        assertThatThrownBy(() -> codec.content(body)).isInstanceOf(CutoverClosureRequestException.class);

        var manual = codec.manual(JsonUtils.parseTree("""
                {"originalFailedCollectionTaskId":"collect-1","deviceId":11,"collectionStage":"TEST",
                 "file":{"purposeCode":"MANUAL_COLLECTION_RESULT","artifactId":71,"versionNo":1,
                 "referenceKey":"manual-ref","fileFactVersion":{"artifactVersion":1,"referenceVersion":1,
                 "availabilityVersion":1},"scopeVersion":1,
                 "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}
                """));
        assertThat(manual.file().purposeCode()).isEqualTo(AttachmentPurpose.MANUAL_COLLECTION_RESULT);
        assertThat(codec.finalResult(JsonUtils.parseTree("{\"finalResult\":\"SUCCESS\"}"))).isEqualTo("SUCCESS");
    }
}
