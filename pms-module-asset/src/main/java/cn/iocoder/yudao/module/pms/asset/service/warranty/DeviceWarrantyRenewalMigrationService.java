package cn.iocoder.yudao.module.pms.asset.service.warranty;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRenewalRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeviceWarrantyRenewalMigrationService implements DeviceWarrantyRenewalMigrationApi {
    private final DeviceWarrantyRenewalRecordMapper mapper;
    private final AdminUserApi users;
    private final PermissionApi permissions;
    static final List<String> BUSINESS_FIELDS = List.of("deviceSn", "warrantyContractNo", "warrantyGrade",
            "warrantyStartDate", "warrantyEndDate", "warrantyMonths", "extended", "isLatest", "remark");
    static final List<String> ALL_FIELDS = List.of("id", "tenantId", "deviceSn", "warrantyContractNo", "warrantyGrade",
            "warrantyStartDate", "warrantyEndDate", "warrantyMonths", "extended", "isLatest", "remark", "version",
            "creator", "createTime", "updater", "updateTime", "deleted");

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public List<Result> preview(Request request) { return execute(request, false); }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Result> apply(Request request) { return execute(request, true); }

    private List<Result> execute(Request request, boolean apply) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (request == null || !Objects.equals(tenant, request.tenantId()) || request.actorId() == null
                || request.actorId() <= 0 || request.owner() == null
                || !request.owner().matches("integration:" + tenant + ":[1-9][0-9]*")
                || request.entries() == null || request.entries().size() > 5000) fail("运行范围无效");
        users.validateUser(request.actorId());
        if (!permissions.hasAnyPermissions(request.actorId(), "pms:integration:execute")
                || !permissions.hasAnyPermissions(request.actorId(), "pms:device:query")) fail("缺少迁移或设备资料权限");
        var keys = new HashSet<String>();
        var targets = new HashSet<Long>();
        var results = new ArrayList<Result>();
        var inserts = new ArrayList<DeviceWarrantyRenewalRecordDO>();
        var updates = new ArrayList<DeviceWarrantyRenewalRecordMapper.Write>();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        for (var entry : request.entries()) {
            if (entry == null || entry.source() == null || entry.source().id() == null || entry.source().id().isBlank()
                    || !Objects.equals(entry.sourceKey(), entry.source().sourceKey()) || entry.sourceKey().length() > 128
                    || !keys.add(entry.sourceKey())) fail("原来源身份缺失或重复");
            var incoming = entity(entry.source());
            var old = entry.targetId() == null ? null : mapper.selectForUpdate(
                    new DeviceWarrantyRenewalRecordMapper.Identity(tenant, entry.targetId()));
            if (entry.targetId() == null && entry.expectedFingerprint() != null
                    || entry.targetId() != null && (!targets.add(entry.targetId()) || old == null
                    || !Objects.equals(old.getTenantId(), tenant) || Boolean.TRUE.equals(old.getDeleted())
                    || !Objects.equals(old.getId(), entry.targetId()) || old.getVersion() == null
                    || !Objects.equals(fingerprint(old), entry.expectedFingerprint()))) fail("正式记录或迁移绑定已变化，禁止覆盖");
            String action;
            if (old == null) {
                incoming.setId(apply ? IdWorker.getId() : null);
                incoming.setTenantId(tenant);
                incoming.setVersion(0);
                incoming.setDeleted(false);
                incoming.setCreator(request.actorId().toString());
                incoming.setUpdater(request.actorId().toString());
                incoming.setCreateTime(now);
                incoming.setUpdateTime(now);
                action = "CREATED";
                inserts.add(incoming);
            } else if (businessFields(old).equals(businessFields(incoming))) {
                incoming = old;
                action = "UNCHANGED";
            } else {
                incoming.setId(old.getId());
                incoming.setTenantId(tenant);
                incoming.setVersion(Math.addExact(old.getVersion(), 1));
                incoming.setDeleted(false);
                incoming.setCreator(old.getCreator());
                incoming.setCreateTime(old.getCreateTime());
                incoming.setUpdater(request.actorId().toString());
                incoming.setUpdateTime(now);
                action = "UPDATED";
                updates.add(new DeviceWarrantyRenewalRecordMapper.Write(incoming, old.getVersion()));
            }
            results.add(new Result(entry.sourceKey(), incoming.getId(), action, fields(incoming, ALL_FIELDS), fingerprint(incoming)));
        }
        // The framework commits business facts, source evidence and bindings in the same chunk transaction.
        if (apply) {
            for (var row : inserts) if (mapper.insertRecord(row) != 1) fail("续保新增冲突");
            for (var command : updates) if (mapper.updateRecord(command) != 1) fail("续保并发更新冲突");
        }
        return List.copyOf(results);
    }

    static DeviceWarrantyRenewalRecordDO entity(Source source) {
        var row = new DeviceWarrantyRenewalRecordDO();
        row.setDeviceSn(limited(source.deviceSn(), 100));
        row.setWarrantyContractNo(limited(source.contractNo(), 64));
        row.setWarrantyGrade(limited(source.grade(), 32));
        row.setWarrantyStartDate(date(source.startDate()));
        row.setWarrantyEndDate(date(source.endDate()));
        if (row.getWarrantyStartDate() != null && row.getWarrantyEndDate() != null
                && row.getWarrantyEndDate().isBefore(row.getWarrantyStartDate())) fail("原起止日期倒置");
        if (source.years() != null && !source.years().isEmpty()) {
            if (!source.years().matches("[0-9]")) fail("原参考年数无效");
            row.setWarrantyMonths(Integer.parseInt(source.years()) * 12);
        }
        row.setExtended(flag(source.extended(), false));
        row.setIsLatest(flag(source.latest(), true));
        if (source.remark() != null && source.remark().getBytes(StandardCharsets.UTF_8).length > 65535) fail("备注超过原TEXT容量");
        row.setRemark(source.remark());
        return row;
    }

    private static String limited(String value, int length) {
        if (value != null && value.codePointCount(0, value.length()) > length) fail("原业务字段超出已批准长度");
        return value;
    }
    private static Boolean flag(String value, boolean latest) {
        if (value == null) return null;
        if ("1".equals(value)) return true;
        if (!latest && "0".equals(value)) return false;
        throw new IllegalArgumentException("续保记录：未定义的原标记值");
    }
    static LocalDate date(String value) {
        if (value == null) return null;
        if (value.length() == 10) return LocalDate.parse(value);
        var date = LocalDateTime.parse(value.replace(' ', 'T'));
        if (!date.toLocalTime().equals(LocalTime.MIDNIGHT)) fail("原日期含非零时间，不能截断精度");
        return date.toLocalDate();
    }
    private static Map<String, Object> businessFields(DeviceWarrantyRenewalRecordDO row) { return fields(row, BUSINESS_FIELDS); }
    private static Map<String, Object> fields(DeviceWarrantyRenewalRecordDO row, List<String> names) {
        var bean = new BeanWrapperImpl(row);
        var result = new LinkedHashMap<String, Object>();
        for (String name : names) {
            Object value = bean.getPropertyValue(name);
            result.put(name, value == null ? tools.jackson.databind.node.NullNode.getInstance()
                    : value instanceof LocalDate || value instanceof LocalDateTime ? value.toString() : value);
        }
        return Collections.unmodifiableMap(result);
    }
    static String fingerprint(DeviceWarrantyRenewalRecordDO row) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    JsonUtils.toJsonString(fields(row, ALL_FIELDS)).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static void fail(String message) { throw new IllegalArgumentException("续保记录：" + message); }
}
