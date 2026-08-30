package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

/** 豁免到期内部命令使用的当前PROJ系统资格端口。 */
public interface ProjectSystemQualificationPort {

    CurrentProjectQualification lockCurrent(Long tenantId, Long projectId);

    record CurrentProjectQualification(
            Long projectId,
            Long currentManagerUserId,
            Integer projectVersion,
            Long participantFactVersion,
            Long treeVersion) {

        public CurrentProjectQualification {
            if (projectId == null || projectId <= 0 || currentManagerUserId == null
                    || currentManagerUserId <= 0 || projectVersion == null || projectVersion < 0
                    || participantFactVersion == null || participantFactVersion < 0
                    || treeVersion == null || treeVersion < 0) {
                throw new IllegalArgumentException("invalid current project qualification");
            }
        }
    }
}
