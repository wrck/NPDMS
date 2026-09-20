package cn.iocoder.yudao.module.pms.engineering.service.training;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_ARGUMENT_INVALID;

final class TrainingSignatureImage {
    private static final String PREFIX = "data:image/png;base64,";
    static String normalize(String value) {
        try {
            if (value == null || !value.startsWith(PREFIX) || value.length() > 350000) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (!"png".equalsIgnoreCase(reader.getFormatName()) || width < 100 || height < 50
                            || width > 1600 || height > 800) throw new IllegalArgumentException();
                    BufferedImage image = reader.read(0);
                    int ink = 0;
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int rgb = image.getRGB(x, y);
                        if ((rgb >>> 24) > 128 && ((rgb >> 16) & 255) < 220 && ((rgb >> 8) & 255) < 220 && (rgb & 255) < 220) ink++;
                    }
                    if (ink < 20) throw new IllegalArgumentException();
                    var output = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", output);
                    return PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch (Exception invalid) { throw exception(TRAINING_ARGUMENT_INVALID, "请提供有效的手写签字 PNG 图片"); }
    }
}
