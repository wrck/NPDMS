package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

public final class SyncQueries {
    private SyncQueries() {}
    public record Id(Long tenantId,Long id) {}
    public record Task(Long tenantId,Long taskId) {}
    public record SourceKeys(Long tenantId,Long taskId,String object,java.util.List<String> sourceKeys) {}
    public record Request(Long tenantId,Long taskId,String requestKey) {}
    public record Due(Long tenantId,LocalDateTime now) {}
    public record TaskIdentity(Long tenantId,String sourceSystem,String adapter) {}
    public record AdapterScope(Long tenantId,String adapter,Long taskId) {}
    @Data @EqualsAndHashCode(callSuper=true)
    public static class Page extends PageParam {
        private Long tenantId;
        private Long taskId;
        private Long parentRunId;
    }
}
