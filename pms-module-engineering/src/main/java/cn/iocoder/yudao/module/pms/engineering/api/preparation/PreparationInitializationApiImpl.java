package cn.iocoder.yudao.module.pms.engineering.api.preparation;

import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationResult;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreparationInitializationApiImpl implements PreparationInitializationApi {

    private final PreparationInitializationService service;

    @Override
    public PreparationInitializationResult initialize(PreparationInitializationCommand command) {
        return service.initialize(command);
    }
}
