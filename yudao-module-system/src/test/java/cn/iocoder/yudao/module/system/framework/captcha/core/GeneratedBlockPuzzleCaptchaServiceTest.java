package cn.iocoder.yudao.module.system.framework.captcha.core;

import cn.iocoder.yudao.module.system.framework.captcha.config.YudaoCaptchaConfiguration;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.impl.CaptchaServiceFactory;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GeneratedBlockPuzzleCaptchaServiceTest {
    @Test
    void generatesDifferentBackgroundsAtTheRequestedDimensions() {
        BufferedImage first = GeneratedBlockPuzzleCaptchaService.background(310, 155);
        BufferedImage second = GeneratedBlockPuzzleCaptchaService.background(310, 155);
        assertEquals(310, first.getWidth());
        assertEquals(155, first.getHeight());
        assertFalse(java.util.Arrays.equals(first.getRGB(0, 0, 310, 155, null, 0, 310),
                second.getRGB(0, 0, 310, 155, null, 0, 310)));
    }

    @Test
    void registrationReusesUpstreamVerificationAndRejectsMissingToken() {
        var original = CaptchaServiceFactory.instances.get("blockPuzzle");
        try {
            YudaoCaptchaConfiguration.generatedBlockPuzzle().postProcessBeanFactory(
                    mock(org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class));
            var service = CaptchaServiceFactory.instances.get("blockPuzzle");
            assertInstanceOf(GeneratedBlockPuzzleCaptchaService.class, service);
            assertFalse(service.check(new CaptchaVO()).isSuccess());
        } finally {
            CaptchaServiceFactory.instances.put("blockPuzzle", original);
        }
    }

    @Test
    void upstreamPuzzleFlowProducesFreshImagesAndRejectsInvalidVerification() {
        var service = new GeneratedBlockPuzzleCaptchaService();
        var properties = new java.util.Properties();
        properties.setProperty("captcha.cacheType", "local");
        properties.setProperty("captcha.water.mark", "SDP");
        service.init(properties);
        var first = service.get(new CaptchaVO());
        var second = service.get(new CaptchaVO());
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        var one = (CaptchaVO) first.getRepData();
        var two = (CaptchaVO) second.getRepData();
        assertNotNull(one.getJigsawImageBase64());
        assertNotEquals(one.getOriginalImageBase64(), two.getOriginalImageBase64());
        var invalid = new CaptchaVO();
        invalid.setToken(one.getToken());
        invalid.setPointJson("invalid");
        assertFalse(service.check(invalid).isSuccess());
    }
}
