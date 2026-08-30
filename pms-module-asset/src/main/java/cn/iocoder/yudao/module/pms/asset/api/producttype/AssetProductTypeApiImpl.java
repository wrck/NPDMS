package cn.iocoder.yudao.module.pms.asset.api.producttype;

import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.service.producttype.AssetProductTypeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetProductTypeApiImpl implements AssetProductTypeApi {

    private final AssetProductTypeQueryService queryService;

    @Override
    public List<ProductTypeCodeResult> getByCodes(ProductTypeCodesQuery query) {
        return queryService.getByCodes(query);
    }

    @Override
    public List<AuthorizedDeviceProductTypeResult> getAuthorizedDeviceProductType(
            AuthorizedDeviceProductTypeQuery query) {
        return queryService.getAuthorizedDeviceProductType(query);
    }
}
