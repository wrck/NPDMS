package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import java.util.List;

/** 已完成上传中与指定文件版本精确对应的原始文件名。 */
public record CompletedFileVersionNamesQuery(Long tenantId, Long artifactId, List<Integer> versionNos) { }
