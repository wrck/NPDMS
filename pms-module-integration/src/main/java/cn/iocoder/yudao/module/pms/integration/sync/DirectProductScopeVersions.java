package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
@lombok.RequiredArgsConstructor final class DirectProductScopeVersions {
 private final DirectScopeLookupMapper mapper;
 long current(DirectScopeLookupMapper.ProjectVersion query){var row=mapper.current(query);return row==null?0:row.getScopeVersion();}
 DirectScopeVersionRow lockExisting(DirectScopeLookupMapper.ProjectVersion query){return mapper.lockExisting(query);}
}
