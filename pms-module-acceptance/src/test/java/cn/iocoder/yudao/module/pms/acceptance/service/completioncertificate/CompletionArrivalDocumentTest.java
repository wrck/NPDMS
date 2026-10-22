package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.CompletionSatisfactionBundleService.CertificateSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class CompletionArrivalDocumentTest {
    @ParameterizedTest
    @CsvSource({"是,2026-10-20,于2026年10月20日完成到货验收","是,NULL,于____年___月___日完成到货验收","否,2026-10-20,完成到货验收","不涉及,2026-10-20,完成到货验收"})
    void generatedDocumentUsesFrozenDateOnlyForYes(String choice,String date,String expected){
        String dateJson="NULL".equals(date)?"null":"\""+date+"\"";
        var proof=JsonUtils.parseObject("{\"itemArrival\":\""+choice+"\",\"arrivalDate\":"+dateJson+",\"devices\":[]}",CertificateSnapshot.class);
        String html=new String(CompletionConfirmationDocumentRenderer.render("COMPLETION",proof,"{}","{}",null,LocalDateTime.of(2026,10,22,10,0)),StandardCharsets.UTF_8);
        assertTrue(html.contains(expected));
        if(!"是".equals(choice)){assertFalse(html.contains("于____年"));assertFalse(html.contains("于2026年"));}
    }
}
