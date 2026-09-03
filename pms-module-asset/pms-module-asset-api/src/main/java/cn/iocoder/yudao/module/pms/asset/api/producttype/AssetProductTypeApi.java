package cn.iocoder.yudao.module.pms.asset.api.producttype;

import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.AuthorizedDeviceProductTypeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;

import java.util.List;

public interface AssetProductTypeApi {

    List<ProductTypeCodeResult> getByCodes(ProductTypeCodesQuery query);

    List<AuthorizedDeviceProductTypeResult> getAuthorizedDeviceProductType(
            AuthorizedDeviceProductTypeQuery query);
}
