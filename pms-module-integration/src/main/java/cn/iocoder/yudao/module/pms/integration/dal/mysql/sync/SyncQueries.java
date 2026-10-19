package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

public final class SyncQueries {
    private SyncQueries() {}
    public record Id(Long tenantId,Long id) {}
    public record Task(Long tenantId,Long taskId) {}
    public record SourceKeys(Long tenantId,Long taskId,String object,String sourceObject,java.util.List<String> sourceKeys) {}
    public record IdentitySources(Long tenantId, String sourceSystem, Long connectionId, String adapter,
                                  String sourceObject, java.util.List<String> sourceKeys, boolean originalOnly) {
        public IdentitySources(Long tenantId,String sourceSystem,Long connectionId,String adapter,String sourceObject,java.util.List<String> sourceKeys) {
            this(tenantId,sourceSystem,connectionId,adapter,sourceObject,sourceKeys,false);
        }
        public IdentitySources originalLine() {return forLine(true);}
        public IdentitySources forLine(boolean originalOnly) {return new IdentitySources(tenantId,sourceSystem,connectionId,adapter,sourceObject,sourceKeys,originalOnly);}
    }
    public record EhrEmployeeUsers(Long tenantId,Long connectionId,java.util.Set<String> sourceEmployeeIds,boolean originalOnly) {
        public EhrEmployeeUsers(Long tenantId,Long connectionId,java.util.Set<String> sourceEmployeeIds) {this(tenantId,connectionId,sourceEmployeeIds,false);}
        public EhrEmployeeUsers originalLine() {return new EhrEmployeeUsers(tenantId,connectionId,sourceEmployeeIds,true);}
    }
    public record InstallationSelection(Long tenantId,Long taskId,java.util.List<String> deviceSns,Long afterId) {}
    public record DeliveryProjectionBindings(Long tenantId,Long taskId,Long runId,String object,String sourceObject,Long afterId) {}
    public record DeliveryBindings(Long tenantId,Long taskId,String object,String sourceObject,Long runId) {}
    public record SourceAdapter(Long tenantId,Long connectionId,String sourceSystem,String adapter) {}
    public record BindingRun(Long tenantId,Long taskId,Long runId,java.util.List<Long> ids,LocalDateTime updatedAt) {}
    public record Request(Long tenantId,Long taskId,String requestKey) {}
    public record Due(Long tenantId,LocalDateTime now) {}
    public record TaskIdentity(Long tenantId,String sourceSystem,String adapter) {}
    public record AdapterScope(Long tenantId,String adapter,Long taskId) {}
    public record ObsoleteOrderLines(Long tenantId,Long taskId,java.util.List<String> sourceKeys) {}
    public record ObsoleteOrderLineDelete(Long tenantId,Long taskId,java.util.List<Long> bindingIds) {}
    @Data @EqualsAndHashCode(callSuper=true)
    public static class Page extends PageParam {
        private Long tenantId;
        private Long taskId;
        private Long parentRunId;
    }
}
