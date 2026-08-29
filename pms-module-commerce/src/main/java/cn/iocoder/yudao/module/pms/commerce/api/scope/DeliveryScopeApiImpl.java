package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import cn.iocoder.yudao.module.pms.commerce.service.scope.DeliveryScopeCompatibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryScopeApiImpl implements DeliveryScopeApi {
    private final DeliveryScopeCompatibilityService deliveryScopeService;

    @Override
    public List<DeliveryScopeSliceDTO> getAvailableSlices(Long parentProjectId, Long expectedScopeVersion) {
        return deliveryScopeService.getAvailableSlices(parentProjectId, expectedScopeVersion);
    }

    @Override
    public SplitScopeApplyResult previewSplit(SplitScopePreviewCommand command) {
        return deliveryScopeService.previewSplit(command);
    }

    @Override
    public SplitScopeApplyResult applySplit(SplitScopeApplyCommand command) {
        return deliveryScopeService.applySplit(command);
    }
}
