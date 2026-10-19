package cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncIdentityStore;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncCounts;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface BaseSyncMetadataMapper {
    record TaskQuery(long tenantId,long taskId,boolean lock) {}
    record RunQuery(long tenantId,long taskId,long runId) {}
    record RunState(long id,boolean preview,String status,java.time.LocalDateTime startedAt) {}
    record Registration(long id,long tenantId,String name,String sourceSystem,BaseSyncConfiguration config,String actor) {}
    record TaskRow(long id,long connectionId,String taskCode,int batchSize,int maxRows,Long activeRunId) {
        public BaseSyncConfiguration configuration() { return new BaseSyncConfiguration(taskCode,connectionId,batchSize,maxRows); }
    }
    record RunRow(long id,long tenantId,long taskId,String requestKey,BaseSyncConfiguration config,String actor,boolean preview) {}
    record FinishRun(long tenantId,long taskId,long runId,SyncCounts counts,String status,String error) {}
    record BindingQuery(long tenantId,long taskId,String object,List<String> sourceKeys,boolean preview) {}
    record BindingWrite(long id,long tenantId,long taskId,long runId,String object,String sourceKey,long targetId,
                        String fingerprint,String previousFingerprint,String actor) {}
    int createTask(@Param("row") Registration row);
    TaskRow selectTask(@Param("query") TaskQuery query);
    RunState selectRun(@Param("query") RunQuery query);
    int createRun(@Param("row") RunRow row);
    int claimTask(@Param("row") RunRow row);
    int finishRun(@Param("command") FinishRun command);
    int releaseTask(@Param("command") FinishRun command);
    List<SyncIdentityStore.Identity> selectBindings(@Param("query") BindingQuery query);
    int insertBinding(@Param("row") BindingWrite row);
    int updateBinding(@Param("row") BindingWrite row);
}
