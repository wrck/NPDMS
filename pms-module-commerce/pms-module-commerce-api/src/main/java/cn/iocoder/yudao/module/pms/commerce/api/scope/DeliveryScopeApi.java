package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;

import java.util.List;

public interface DeliveryScopeApi {

    List<DeliveryScopeSliceDTO> getAvailableSlices(Long parentProjectId, Long expectedScopeVersion);

    SplitScopeApplyResult previewSplit(SplitScopePreviewCommand command);

    SplitScopeApplyResult applySplit(SplitScopeApplyCommand command);
}
