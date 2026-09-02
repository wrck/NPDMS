package cn.iocoder.yudao.module.pms.cutover.api.spare;

import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareExternalReferenceBindingCommand;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareExternalReferenceBindingResult;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareStatusCallbackCommand;
import cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareStatusCallbackResult;

/** CUT-08供INT-06回写外部申请引用与原始状态事实的公开业务接口。 */
public interface CutoverSpareCallbackApi {

    SpareExternalReferenceBindingResult bindExternalReference(SpareExternalReferenceBindingCommand command);

    SpareStatusCallbackResult acceptStatus(SpareStatusCallbackCommand command);
}
