package cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** BPM owns definitions, candidate validation, actual approval tasks and immutable process history. */
public interface DefaultBusinessApprovalProcessApi {
    String BACKEND = "BPM_DEFAULT_BUSINESS";
    record Definition(String id, String key, String name, int version, String formConf,
                      List<String> formFields, boolean available, String unavailableReason) { }
    record Node(String id, String name, boolean selectApprovers) { }
    record Preview(List<Node> nodes, Map<String,String> fieldPermissions) { }
    record Context(EntityRef entity, Long projectId, Long version, String attemptId, Long actorId) { }
    record Command(Context context, String definitionId, Map<String,Object> values,
                   Map<String,Object> variables, Map<String,List<Long>> selectedApprovers) { }
    record Result(Context context, String definitionId, String instanceId, String status, String reason) { }
    record Opinion(String userId, String action, String comment, LocalDateTime time) { }
    record Detail(String name, Map<String,Object> submittedValues, List<Opinion> opinions) { }

    List<Definition> definitions(Long actorId);
    Preview preview(Command command);
    Result start(Command command);
    Result cancel(Context context, String instanceId, String reason, Long actorId);
    Detail detail(Context context, String instanceId);
}
