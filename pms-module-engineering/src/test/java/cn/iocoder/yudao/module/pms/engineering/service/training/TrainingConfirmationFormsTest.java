package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class TrainingConfirmationFormsTest {
    static String png(boolean signed) throws Exception {
        var image = new BufferedImage(600, 270, BufferedImage.TYPE_INT_ARGB);
        if (signed) {
            var graphics = image.createGraphics();
            graphics.setColor(Color.BLACK);
            graphics.drawLine(20, 20, 160, 130);
            graphics.drawLine(160, 130, 250, 30);
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    @Test void preservesPngAndRejectsEmptyOrDisguisedImages() throws Exception {
        assertTrue(TrainingSignatureImage.normalize(png(true)).startsWith("data:image/png;base64,"));
        assertThrows(RuntimeException.class, () -> TrainingSignatureImage.normalize(png(false)));
        assertThrows(RuntimeException.class, () -> TrainingSignatureImage.normalize("data:image/png;base64,PHN2Zz4="));
        assertThrows(RuntimeException.class, () -> TrainingSignatureImage.normalize(null));
    }

    @Test void stripsExecutableConfigurationAndKeepsNativeSignature() {
        String input = TrainingConfirmationForms.defaults().replace("\"type\": \"radio\"", "\"on\":{\"change\":\"alert(1)\"},\"type\": \"radio\"");
        String snapshot = TrainingConfirmationForms.safeSnapshot(input);
        assertFalse(snapshot.contains("alert"));
        assertTrue(snapshot.contains("signaturePad"));
    }

    @Test void preventsRemovingBusinessFieldsOrChangingRatingValues() {
        assertThrows(RuntimeException.class, () -> TrainingConfirmationForms.safeSnapshot("[]"));
        assertThrows(RuntimeException.class, () -> TrainingConfirmationForms.safeSnapshot(
                TrainingConfirmationForms.defaults().replace("非常满意", "额外评分")));
    }

    @Test void validatesRequiredSupplementAndDoesNotPersistUnknownValues() {
        String defaults = TrainingConfirmationForms.defaults().trim();
        String rules = TrainingConfirmationForms.safeSnapshot(defaults.substring(0, defaults.length() - 1)
                + ",{\"type\":\"input\",\"field\":\"department\",\"title\":\"部门\",\"validate\":[{\"required\":true}]}]");
        var values = new java.util.LinkedHashMap<String, Object>(Map.of("skillRating", "很好", "effectRating", "良好",
                "satisfactionRating", "非常满意", "signConfirmerName", "张三"));
        assertThrows(RuntimeException.class, () -> TrainingConfirmationForms.validateValues(rules, values));
        values.put("department", "运维部");
        values.put("unconfigured", "ignore");
        var saved = JsonUtils.parseTree(TrainingConfirmationForms.validateValues(rules, values));
        assertEquals("运维部", saved.get("department").asText());
        assertFalse(saved.has("unconfigured"));
    }
}
