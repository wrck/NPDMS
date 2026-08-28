package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import java.io.InputStream;

public record DeviceConfigurationFileContent(String fileName, InputStream content) {
}
