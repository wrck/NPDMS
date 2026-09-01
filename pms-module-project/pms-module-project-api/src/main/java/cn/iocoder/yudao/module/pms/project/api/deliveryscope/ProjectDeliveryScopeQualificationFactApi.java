package cn.iocoder.yudao.module.pms.project.api.deliveryscope;

import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationQuery;
import cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto.ProjectDeliveryScopeQualificationRevalidationQuery;

/** PROJ为COM交付范围写命令提供的项目经理、生命周期与ACTION_EDIT组合事实。 */
public interface ProjectDeliveryScopeQualificationFactApi {

    /** 无锁读取当前组合资格事实；tenantId必须与受信租户上下文一致。 */
    ProjectDeliveryScopeQualificationFact inspect(ProjectDeliveryScopeQualificationQuery query);

    /** 按冻结版本锁定重验当前组合资格事实；版本或资格变化使用稳定公共错误分类。 */
    ProjectDeliveryScopeQualificationFact lockAndRevalidate(
            ProjectDeliveryScopeQualificationRevalidationQuery query);
}
