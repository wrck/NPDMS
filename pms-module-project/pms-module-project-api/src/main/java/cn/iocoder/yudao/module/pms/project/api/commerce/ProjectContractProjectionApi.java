package cn.iocoder.yudao.module.pms.project.api.commerce;

/** Internal projection refresh; caller must already authorize and hold its transaction. */
public interface ProjectContractProjectionApi {
    void refresh(ProjectContractProjectionRefresh request);

    record ProjectContractProjectionRefresh(Long tenantId, Long projectId) { }
}
