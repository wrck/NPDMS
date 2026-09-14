package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import java.util.List;

public record FileReferenceSetsQuery(Long tenantId, List<FileReferenceSetKey> keys) {
    public FileReferenceSetsQuery {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }
}
