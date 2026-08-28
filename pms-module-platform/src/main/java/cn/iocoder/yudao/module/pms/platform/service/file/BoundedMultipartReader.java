package cn.iocoder.yudao.module.pms.platform.service.file;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SIZE_EXCEEDED;

@Component
public class BoundedMultipartReader {

    private static final int BUFFER_SIZE = 64 * 1024;

    public byte[] read(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || maxBytes <= 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        if (file.getSize() > maxBytes) {
            throw exception(FILE_SIZE_EXCEEDED);
        }
        int initialSize = (int) Math.min(file.getSize(), BUFFER_SIZE);
        try (InputStream input = file.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw exception(FILE_SIZE_EXCEEDED);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }
}
