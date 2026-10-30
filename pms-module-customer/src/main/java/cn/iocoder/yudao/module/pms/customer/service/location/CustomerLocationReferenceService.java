package cn.iocoder.yudao.module.pms.customer.service.location;

import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.location.CustomerLocationReferenceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.location.CustomerLocationReferenceMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.location.query.CurrentCustomerLocationListQuery;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.location.query.CurrentCustomerLocationQuery;
import cn.iocoder.yudao.module.pms.customer.service.location.command.CustomerLocationCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class CustomerLocationReferenceService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("ADDRESS", "SITE");

    @Resource
    private AssetLocationApi assetLocationApi;
    @Resource
    private CustomerMasterMapper customerMasterMapper;
    @Resource
    private CustomerLocationReferenceMapper locationReferenceMapper;

    public List<CustomerLocationReferenceDO> listCurrent(Long tenantId, Long customerId) {
        if (tenantId == null || customerId == null) {
            throw new IllegalArgumentException("客户地点引用查询不完整");
        }
        return locationReferenceMapper.selectCurrentList(
                new CurrentCustomerLocationListQuery(tenantId, customerId));
    }

    @Transactional(rollbackFor = Exception.class)
    public CustomerLocationReferenceDO maintain(CustomerLocationCommand command) {
        validate(command);
        CustomerMasterDO customer = customerMasterMapper.selectById(command.customerId());
        if (customer == null || !Objects.equals(customer.getTenantId(), command.tenantId())) {
            throw new IllegalArgumentException("客户地点引用跨租户或客户不存在");
        }
        String addressSnapshot = resolveAddressSnapshot(command);
        LocalDateTime now = LocalDateTime.now();
        CustomerLocationReferenceDO current = locationReferenceMapper.selectCurrent(
                new CurrentCustomerLocationQuery(command.tenantId(), command.customerId(), command.locationType()));
        if (current != null) {
            current.setEffectiveTo(now);
            locationReferenceMapper.updateById(current);
        }
        CustomerLocationReferenceDO reference = new CustomerLocationReferenceDO();
        reference.setTenantId(command.tenantId());
        reference.setCustomerId(command.customerId());
        reference.setLocationType(command.locationType());
        reference.setLocationId(command.locationId());
        reference.setSourceVersion(command.sourceVersion());
        reference.setEffectiveFrom(now);
        locationReferenceMapper.insert(reference);
        customer.setAddress(addressSnapshot);
        customerMasterMapper.updateById(customer);
        return reference;
    }

    private void validate(CustomerLocationCommand command) {
        if (command == null || command.tenantId() == null || command.customerId() == null
                || !SUPPORTED_TYPES.contains(command.locationType()) || command.locationId() == null
                || command.sourceVersion() == null || command.sourceVersion() < 0
                || command.operationId() == null || command.operationId().isBlank()) {
            throw new IllegalArgumentException("客户地点引用命令不完整");
        }
    }

    private String resolveAddressSnapshot(CustomerLocationCommand command) {
        if ("ADDRESS".equals(command.locationType())) {
            return composeAddressSnapshot("ADDRESS",
                    assetLocationApi.getAddress(command.locationId(), command.sourceVersion()), null);
        }
        SiteRespDTO site = assetLocationApi.getSite(command.locationId(), command.sourceVersion());
        return composeAddressSnapshot("SITE", site.addressId() == null ? null
                : assetLocationApi.getAddress(site.addressId(), null), site);
    }

    static String composeAddressSnapshot(String locationType, AddressRespDTO address, SiteRespDTO site) {
        if ("ADDRESS".equals(locationType)) {
            return address == null ? null : address.fullAddress();
        }
        if (site == null) {
            return null;
        }
        String addressText = address == null ? null : address.fullAddress();
        return addressText == null || addressText.isBlank() ? site.name() : site.name() + "（" + addressText + "）";
    }
}
