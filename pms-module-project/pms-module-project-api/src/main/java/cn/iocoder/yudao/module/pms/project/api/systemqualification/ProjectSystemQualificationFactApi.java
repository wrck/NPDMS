package cn.iocoder.yudao.module.pms.project.api.systemqualification;

import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;

/** PROJ为无用户主体内部命令提供的当前项目资格锁定事实。 */
public interface ProjectSystemQualificationFactApi {

    /**
     * 锁定当前项目、唯一项目经理事实和当前根树版本，并校验请求的生命周期与阶段。
     *
     * <p>租户只取受信调用上下文；本契约不执行用户{@code ACTION_EDIT}授权，也不接收消费方冻结版本。</p>
     */
    ProjectSystemQualificationFact lockCurrentForSystem(ProjectSystemQualificationLockQuery query);

}
