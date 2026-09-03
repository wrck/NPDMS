package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.module.pms.asset.api.producttype.AssetProductTypeApi;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InspectionAssetProductTypeApiImpl implements InspectionAssetProductTypeApi {

    private final AssetProductTypeApi assetProductTypeApi;

    @Override
    public List<ProductTypeCodeResult> getByCodes(ProductTypeCodesQuery query) {
        return AssetProductTypeCallerContext.callAsInspection(
                () -> assetProductTypeApi.getByCodes(query));
    }

    @Override
    public List<AuthorizedDeviceProductTypeResult> getAuthorizedDeviceProductType(
            AuthorizedDeviceProductTypeQuery query) {
        return AssetProductTypeCallerContext.callAsInspection(
                () -> assetProductTypeApi.getAuthorizedDeviceProductType(query));
    }
}
