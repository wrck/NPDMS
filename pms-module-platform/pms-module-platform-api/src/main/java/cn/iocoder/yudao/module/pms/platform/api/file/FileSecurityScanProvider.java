package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanResult;

public interface FileSecurityScanProvider {

    FileSecurityScanResult scan(FileSecurityScanCommand command);
}
