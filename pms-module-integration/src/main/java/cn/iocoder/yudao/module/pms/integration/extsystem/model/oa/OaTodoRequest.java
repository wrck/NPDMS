package cn.iocoder.yudao.module.pms.integration.extsystem.model.oa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OA (致远 OA) todo push request payload.
 *
 * <p>Used by {@link cn.iocoder.yudao.module.pms.integration.extsystem.service.OaIntegrationService#pushTodo}
 * to create a pending todo item in the OA system for a Flowable user task.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OaTodoRequest {

    /** Todo title, typically the Flowable task name. */
    private String title;

    /** Todo content / description. */
    private String content;

    /** Handler user id in OA (mapped from the Flowable task assignee). */
    private String handlerUserId;

    /** Handler user display name in OA. */
    private String handlerUserName;

    /** Flowable process instance id. */
    private String processInstanceId;

    /** Business key associated with the process instance. */
    private String businessKey;

    /** Business type code (e.g. project approval, settlement approval). */
    private String businessType;

    /** URL for the user to open the process detail page. */
    private String processUrl;
}
