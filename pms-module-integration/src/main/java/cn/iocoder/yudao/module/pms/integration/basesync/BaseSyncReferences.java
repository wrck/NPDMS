package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncReferences;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import java.util.List;

/** Shared MyBatis reference reads; scope and batching are supplied by AbstractSyncTask. */
@Component
@RequiredArgsConstructor
public class BaseSyncReferences implements SyncReferences {
    private final ReferenceMapper mapper;

    @Override public List<AuditUser> findAuditUsers(AuditUsersQuery query) {
        if (query.tenantId() <= 0 || query.usernames().size() > 200
                || query.usernames().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("审计账号引用查询范围无效");
        if (query.usernames().isEmpty()) return List.of();
        // Database collation may be case-insensitive; the reference is an exact username.
        return mapper.selectAuditUsers(query).stream()
                .filter(user -> query.usernames().contains(user.username())).toList();
    }

    @Mapper
    public interface ReferenceMapper {
        List<AuditUser> selectAuditUsers(@Param("query") AuditUsersQuery query);
        List<AuditUser> selectAuditUserCatalogue(@Param("query") AuditCatalogueQuery query);
    }

    @Override public List<AuditUser> findAuditUserCatalogue(AuditCatalogueQuery query){
        if(query.tenantId()<=0)throw new IllegalArgumentException("审计账号目录租户无效");
        return mapper.selectAuditUserCatalogue(query);
    }
}
