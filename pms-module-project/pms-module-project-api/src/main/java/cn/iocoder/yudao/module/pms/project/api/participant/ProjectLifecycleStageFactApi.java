package cn.iocoder.yudao.module.pms.project.api.participant;

/** Internal Owner fact: standard lifecycle membership, not the earliest current_stage summary. */
public interface ProjectLifecycleStageFactApi {
    boolean isActive(Query query);
    record Query(Long projectId, Integer expectedProjectVersion, String lifecycleStage) { }
}
