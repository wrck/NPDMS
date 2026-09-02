package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalCommandResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFactQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalInspectResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalPauseCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalRevalidationQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalRevalidationResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartResult;

/** F-CUT-005拥有的割接审批事实公开合同。 */
public interface CutoverApprovalFactApi {

    /** 必须加入调用方已开启的CUT提交事务。 */
    CutoverApprovalStartResult start(CutoverApprovalStartCommand command);

    CutoverApprovalInspectResult inspect(CutoverApprovalFactQuery query);

    /** 必须加入调用方已开启的CUT写事务并锁定当前审批事实。 */
    CutoverApprovalRevalidationResult lockAndRevalidate(CutoverApprovalRevalidationQuery query);

    /** 必须加入调用方已开启的CUT来源失效事务。 */
    CutoverApprovalCommandResult pauseForSourceInvalidation(CutoverApprovalPauseCommand command);
}
