package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OfficeProjectReadResponseAdviceTest {
    @AfterEach void clear() { OfficeProjectReadContext.clear(); }
    @Test void removesLegacyFileLinksAndCommandTokensOnlyFromReadonlyResponseWithoutChangingOriginalHistory() {
        var original = Map.of("name", "培训历史", "fileUrl", "https://files.invalid/original.pdf",
                "attachmentFiles", "https://files.invalid/a.pdf,https://files.invalid/b.pdf",
                "attachments", List.of(Map.of("url", "/legacy/file", "fileName", "原件.pdf", "confirmToken", "test-confirm")));
        var result = CommonResult.success(original);
        var advice = new OfficeProjectReadResponseAdvice();
        assertSame(result, advice.beforeBodyWrite(result,null,null,null,null,null));
        OfficeProjectReadContext.set(new OfficeProjectReadContext.Scope(1L,9L,42L));
        var response = advice.beforeBodyWrite(result,null,null,null,null,null);
        var data = JsonUtils.getObjectMapper().valueToTree(response).get("data");
        assertEquals("培训历史", data.get("name").asText());
        assertNull(data.get("fileUrl"));
        assertNull(data.get("attachmentFiles"));
        assertNull(data.get("attachments").get(0).get("url"));
        assertNull(data.get("attachments").get(0).get("confirmToken"));
        assertEquals("原件.pdf", data.get("attachments").get(0).get("fileName").asText());
        assertTrue(original.containsKey("fileUrl"));
        assertTrue(original.containsKey("attachmentFiles"));
        assertEquals("https://files.invalid/a.pdf,https://files.invalid/b.pdf", original.get("attachmentFiles"));
    }
}
