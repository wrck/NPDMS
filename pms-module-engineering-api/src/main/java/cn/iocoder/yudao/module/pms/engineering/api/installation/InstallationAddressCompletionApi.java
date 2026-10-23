package cn.iocoder.yudao.module.pms.engineering.api.installation;
/** 工程Owner的项目硬件安装地址完成事实；不执行任务生命周期写入。 */
public interface InstallationAddressCompletionApi {
    record Query(Long tenantId, Long projectId) { }
    record Fact(int totalDevices, int addressedDevices) {
        public boolean completed() { return totalDevices > 0 && totalDevices == addressedDevices; }
    }
    /** 必须在调用方事务内锁定设备范围和安装记录后核对。 */
    Fact lockAndInspect(Query query);
}
