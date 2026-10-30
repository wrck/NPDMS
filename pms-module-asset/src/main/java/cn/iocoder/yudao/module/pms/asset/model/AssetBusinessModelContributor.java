package cn.iocoder.yudao.module.pms.asset.model;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.product.AssetProductOfficialDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.product.AssetProductOfficialMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * B4 批次统一目录声明（P12）：设备档案、站点、官网产品、产品类型进入统一目录。
 * 设备指派/位置解析状态机、位置树、受控导入等专业写路径保留本域；
 * 版本/保修/日志/发运/装配与指派投影等子表随设备专业路径，不进入目录。
 */
@Component
public class AssetBusinessModelContributor implements BusinessModelContributor {

    private final DeviceMapper deviceMapper;
    private final SiteMapper siteMapper;
    private final AssetProductOfficialMapper assetProductOfficialMapper;
    private final AssetProductTypeMapper assetProductTypeMapper;

    public AssetBusinessModelContributor(DeviceMapper deviceMapper,
                                         SiteMapper siteMapper,
                                         AssetProductOfficialMapper assetProductOfficialMapper,
                                         AssetProductTypeMapper assetProductTypeMapper) {
        this.deviceMapper = deviceMapper;
        this.siteMapper = siteMapper;
        this.assetProductOfficialMapper = assetProductOfficialMapper;
        this.assetProductTypeMapper = assetProductTypeMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        List<BusinessModelDeclaration> declarations = new ArrayList<>();
        BusinessModelDescriptor device = new BusinessModelDescriptor("AST", "device",
                "AST_DEVICE", 1, BusinessModelKind.AGGREGATE_ROOT, "设备",
                "pms:device:query",
                List.of(required("sn", "设备 SN", EntityField.Type.TEXT),
                        field("name", "设备名称", EntityField.Type.TEXT),
                        field("productCode", "产品编码", EntityField.Type.TEXT),
                        field("productModel", "产品型号", EntityField.Type.TEXT),
                        field("productName", "产品名称", EntityField.Type.TEXT),
                        field("shipmentTime", "发运时间", EntityField.Type.DATETIME),
                        field("packageNo", "发包号", EntityField.Type.TEXT),
                        field("contractNo", "合同编号", EntityField.Type.TEXT),
                        field("projectId", "项目编号", EntityField.Type.NUMBER),
                        field("companyName", "公司名称", EntityField.Type.TEXT),
                        field("departmentCode", "部门编码", EntityField.Type.TEXT),
                        field("customerId", "客户编号", EntityField.Type.NUMBER),
                        field("siteId", "站点编号", EntityField.Type.NUMBER),
                        field("siteLocationId", "位置编号", EntityField.Type.NUMBER),
                        field("locationResolutionStatus", "位置解析状态", EntityField.Type.TEXT),
                        field("warrantyStartDate", "保修开始日期", EntityField.Type.DATE),
                        field("warrantyEndDate", "保修结束日期", EntityField.Type.DATE),
                        field("warrantyStatus", "保修状态", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("sourceSystem", "来源系统", EntityField.Type.TEXT),
                        field("sourceVersion", "来源版本", EntityField.Type.TEXT),
                        field("syncStatus", "同步状态", EntityField.Type.TEXT),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "ast_device");
        declarations.add(new BusinessModelDeclaration(device, DeviceDO.class, deviceMapper, null));
        BusinessModelDescriptor site = new BusinessModelDescriptor("AST", "site",
                "AST_SITE", 1, BusinessModelKind.AGGREGATE_ROOT, "站点",
                "pms:asset-location:query",
                List.of(required("code", "站点编码", EntityField.Type.TEXT),
                        required("name", "站点名称", EntityField.Type.TEXT),
                        field("customerId", "客户编号", EntityField.Type.NUMBER),
                        field("addressId", "地址编号", EntityField.Type.NUMBER),
                        field("siteType", "站点类型", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "ast_site");
        declarations.add(new BusinessModelDeclaration(site, SiteDO.class, siteMapper, null));
        BusinessModelDescriptor productOfficial = new BusinessModelDescriptor("AST", "assetProductOfficial",
                "AST_PRODUCT_OFFICIAL", 1, BusinessModelKind.AGGREGATE_ROOT, "官网产品信息",
                "pms:imp-material-exch:query",
                List.of(required("productCode", "产品编码", EntityField.Type.TEXT),
                        field("productName", "产品名称", EntityField.Type.TEXT),
                        field("productModel", "产品型号", EntityField.Type.TEXT),
                        field("productDesc", "产品描述", EntityField.Type.TEXT),
                        field("technicalSpec", "技术规格", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "ast_product_official_info");
        declarations.add(new BusinessModelDeclaration(productOfficial, AssetProductOfficialDO.class,
                assetProductOfficialMapper, null));
        BusinessModelDescriptor productType = new BusinessModelDescriptor("AST", "assetProductType",
                "AST_PRODUCT_TYPE", 1, BusinessModelKind.AGGREGATE_ROOT, "产品类型",
                "pms:asset-product-type:controlled-import",
                List.of(required("typeCode", "类型编码", EntityField.Type.TEXT),
                        required("displayName", "显示名称", EntityField.Type.TEXT),
                        field("enabled", "启用", EntityField.Type.BOOLEAN),
                        field("sourceSystem", "来源系统", EntityField.Type.TEXT),
                        field("sourceVersion", "来源版本", EntityField.Type.TEXT),
                        field("syncStatus", "同步状态", EntityField.Type.TEXT),
                        field("syncedAt", "最近同步时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "ast_product_type");
        declarations.add(new BusinessModelDeclaration(productType, AssetProductTypeDO.class,
                assetProductTypeMapper, null));
        return declarations;
    }
}
