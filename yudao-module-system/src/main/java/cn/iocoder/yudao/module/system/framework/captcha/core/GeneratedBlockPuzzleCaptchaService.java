package cn.iocoder.yudao.module.system.framework.captcha.core;

import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.impl.BlockPuzzleCaptchaServiceImpl;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

/** Generates a new background while retaining the upstream puzzle and verification flow. */
public final class GeneratedBlockPuzzleCaptchaService extends BlockPuzzleCaptchaServiceImpl {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public CaptchaVO pictureTemplatesCut(BufferedImage original, BufferedImage template, String templateName) {
        BufferedImage generated = background(original.getWidth(), original.getHeight());
        Graphics2D graphics = generated.createGraphics();
        try {
            graphics.setFont(waterMarkFont);
            graphics.setColor(Color.WHITE);
            graphics.drawString(waterMark, generated.getWidth() - getEnOrChLength(waterMark),
                    generated.getHeight() - HAN_ZI_SIZE / 2 + 7);
        } finally {
            graphics.dispose();
        }
        return super.pictureTemplatesCut(generated, template, templateName);
    }

    static BufferedImage background(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new GradientPaint(0, 0, color(100, 150), width, height, color(100, 150)));
            graphics.fillRect(0, 0, width, height);
            for (int i = 0; i < 36; i++) {
                Path2D shape = new Path2D.Double();
                shape.moveTo(RANDOM.nextInt(width), RANDOM.nextInt(height));
                for (int point = 0; point < 4; point++) shape.lineTo(RANDOM.nextInt(width), RANDOM.nextInt(height));
                shape.closePath();
                Color color = color(35, 195);
                graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45 + RANDOM.nextInt(90)));
                graphics.fill(shape);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Color color(int base, int range) {
        return new Color(base + RANDOM.nextInt(range), base + RANDOM.nextInt(range), base + RANDOM.nextInt(range));
    }
}
