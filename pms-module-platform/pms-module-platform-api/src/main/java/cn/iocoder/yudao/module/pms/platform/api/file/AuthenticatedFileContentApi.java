package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;

/** 有界内部读取；继承正式用户文件下载授权及 Owner 精确版本策略，不返回存储位置。 */
public interface AuthenticatedFileContentApi {
    record ReadCommand(FileArtifactVersionQuery file, long maxBytes) { }
    byte[] read(ReadCommand command);
}
