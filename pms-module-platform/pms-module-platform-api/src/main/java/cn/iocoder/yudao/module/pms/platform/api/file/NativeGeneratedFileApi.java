package cn.iocoder.yudao.module.pms.platform.api.file;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFileCommand;
public interface NativeGeneratedFileApi {
    record RegisteredFile(Long materialId,Long referenceId,Long artifactId,Integer versionNo,String sha256,String fileName) {}
    RegisteredFile create(NativeGeneratedFileCommand command);
    String requestDownload(String ownerModule,String entityType,Long entityId,Long materialId);
}
