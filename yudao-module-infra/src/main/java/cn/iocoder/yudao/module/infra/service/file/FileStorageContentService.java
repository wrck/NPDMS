package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class FileStorageContentService implements FileStorageContentApi {
    private final FileService files;
    @Override public byte[] read(Query query) {
        if (query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.infraFileId()==null || query.expectedSize()==null || query.expectedSize()<1
                || query.maxBytes()<1 || query.maxBytes()>50*1024*1024 || query.expectedSize()>query.maxBytes()
                || query.expectedSha256()==null || !query.expectedSha256().matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("FILE_STORAGE_CONTENT_IDENTITY_INVALID");
        var file=files.getFile(query.infraFileId());
        if (file==null || !Objects.equals(file.getSize(),query.expectedSize()) || !FileReceiptDownloadService.isReceiptPath(file.getPath()))
            throw new IllegalStateException("FILE_STORAGE_CONTENT_RECEIPT_INVALID");
        cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils.validatePath(file.getPath());
        try {
            byte[] content=files.getFileContent(file.getConfigId(),file.getPath());
            if(content==null || content.length!=query.expectedSize()
                    || !org.apache.commons.codec.digest.DigestUtils.sha256Hex(content).equals(query.expectedSha256()))
                throw new IllegalStateException("FILE_STORAGE_CONTENT_CHANGED");
            return content;
        } catch(RuntimeException failure) { throw failure; }
        catch(Exception failure) { throw new IllegalStateException("FILE_STORAGE_CONTENT_READ_FAILED"); }
    }
}
