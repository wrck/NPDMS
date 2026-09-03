package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "pms.asset-product-type")
@Data
class AssetProductTypeServicePrincipalProperties {

    private Map<String, TrustedAssetProductTypeServicePrincipalRegistry.TrustedPrincipal>
            trustedServicePrincipals = new HashMap<>();
}
