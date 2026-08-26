package cn.iocoder.yudao.module.infra.api.job;

/** 供最终应用按稳定处理器名同步既有 Quartz 任务的窄公共契约。 */
public interface JobApi {

    void syncEnabledJobByHandlerName(String handlerName);
}
