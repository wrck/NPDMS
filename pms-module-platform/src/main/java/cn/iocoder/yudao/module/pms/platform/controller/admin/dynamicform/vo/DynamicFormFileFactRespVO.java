package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;

public record DynamicFormFileFactRespVO(Long artifactId, Integer versionNo, String referenceKey,
                                        FileFactVersion fileFactVersion, Long scopeVersion, String status) {
}
