package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationHistoricalCarryUpdate;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsInstallationRecordMigrationServiceTest {
    InstallationMapper records=mock(InstallationMapper.class);
    ProjectDeviceSelectionApi devices=mock(ProjectDeviceSelectionApi.class);
    AssetLocationApi locations=mock(AssetLocationApi.class);
    SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);
    DppmsInstallationRecordMigrationService service=new DppmsInstallationRecordMigrationService(records,devices,locations,users);
    InstallationDO original;
    LocalDateTime created=LocalDateTime.of(2020,1,2,3,4,5);
    static final String ORIGINAL="""
            {"id":"700","tenant_id":"1","project_id":"100","code":"DPPMS-301","equipment_id":"500",
             "install_location":"原地址","address_id":"900","address_version":"0","site_id":null,"site_version":null,
             "site_location_id":null,"site_location_version":null,"location_resolution_status":"UNRESOLVED",
             "address_snapshot":ADDRESS_JSON,"location_snapshot":null,
             "effective_from":null,"effective_to":null,"install_time":null,"installer_user_id":null,
             "environment_check":null,"spec_check":null,"photo_url":null,"result":null,"status":"0",
             "remark":"DPPMS历史安装地址候选；未推断完成","version":"0","creator":"1","create_time":"2026-10-09T01:05:35",
             "updater":"1","update_time":"2026-10-09T01:05:35","deleted":"false"}
            """.replace("ADDRESS_JSON",JsonUtils.toJsonString("{\"fullAddress\":\"原地址\"}"));
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);original=new InstallationDO();original.setId(700L);original.setTenantId(1L);
        original.setProjectId(100L);original.setCode("DPPMS-301");original.setEquipmentId(500L);original.setInstallLocation("原地址");
        original.setAddressId(900L);original.setAddressVersion(0L);original.setAddressSnapshot("{\"fullAddress\":\"原地址\"}");
        original.setLocationResolutionStatus("UNRESOLVED");original.setStatus(0);original.setVersion(0L);original.setDeleted(false);
        original.setRemark("DPPMS历史安装地址候选；未推断完成");original.setCreator("1");original.setUpdater("1");
        original.setCreateTime(LocalDateTime.of(2026,10,9,1,5,35));original.setUpdateTime(original.getCreateTime());
        when(records.selectSourceCodeForUpdate(any())).thenAnswer(call->original);
        when(devices.validateSelection(100L,List.of(500L))).thenReturn(List.of(new SelectedProjectDevice(500L,"SN",null,null,null,null)));
        when(users.getHistoricalIdentitiesByUsername(1L,Set.of("c00001"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(31L,"c00001")));
        when(records.carryHistoricalRecord(any())).thenReturn(1);
    }
    @AfterEach void teardown(){TenantContextHolder.clear();}
    Command command(Baseline baseline) {
        var raw=new LinkedHashMap<String,String>();cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS.forEach(key->raw.put(key,null));
        raw.putAll(Map.of("id","301","projectId","10","barcode","SN","installAddress","原地址","createTime",created.toString(),"createBy","c00001","transferFlag","-1"));
        return new Command(1L,1L,100L,"10",List.of(new Entry("301",raw,baseline)));
    }
    Baseline baseline(){return new Baseline(700L,ORIGINAL,null,null);}
    @Test void previewPreservesIdAndPlansApprovedDateAuditWithoutWriting() {
        var result=service.preview(command(baseline())).getFirst();assertEquals(700L,result.targetId());assertEquals("UPDATED",result.action());
        var after=JsonUtils.parseObject(result.after().formalFieldsJson(),Map.class);
        assertEquals("2",after.get("status"));assertEquals("31",after.get("creator"));assertNull(after.get("updater"));
        assertEquals(created.toString(),after.get("create_time"));assertEquals(created.toString(),after.get("install_time"));
        assertEquals(created.toString(),after.get("effective_from"));assertEquals(created.toString(),after.get("update_time"));
        assertEquals("1",after.get("version"));assertNull(after.get("effective_to"));
        verify(records,never()).carryHistoricalRecord(any());verifyNoInteractions(locations);
    }
    @Test void applyAndReplayDoNotRepeatHistoricalAuditOrLocationWrites() {
        var input=command(baseline());var applied=service.apply(input).getFirst();
        var capture=ArgumentCaptor.forClass(InstallationHistoricalCarryUpdate.class);verify(records).carryHistoricalRecord(capture.capture());
        original=capture.getValue().record();when(records.selectCurrentRecordForUpdate(any())).thenAnswer(call->original);
        assertEquals("UNCHANGED",service.apply(command(applied.after())).getFirst().action());
        verify(records,times(1)).carryHistoricalRecord(any());
        verify(locations,times(2)).effectEquipmentLocationIfAbsent(argThat(c->c.equipmentId().equals(500L)
                && c.installationId().equals(700L)&&c.effectiveFrom().equals(created)));
    }
    @Test void fullBatchUsesOneCompleteAuthorizedDeviceValidationBeforeWriting() {
        var second=copyForNewer();second.setId(701L);second.setEquipmentId(501L);second.setCode("DPPMS-302");second.setStatus(0);second.setInstallTime(null);second.setEffectiveFrom(null);
        when(records.selectSourceCodeForUpdate(any())).thenAnswer(call->((cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationSourceCodeQuery)call.getArgument(0)).code().equals("DPPMS-301")?original:second);
        when(devices.validateSelection(100L,List.of(500L,501L))).thenReturn(List.of(new SelectedProjectDevice(500L,"SN",null,null,null,null),new SelectedProjectDevice(501L,"SN2",null,null,null,null)));
        var one=command(baseline());var raw2=new LinkedHashMap<>(one.entries().getFirst().originalFields());raw2.put("id","302");raw2.put("barcode","SN2");
        var secondBaseline=new Baseline(701L,ORIGINAL.replace("\"700\"","\"701\"").replace("\"500\"","\"501\"").replace("DPPMS-301","DPPMS-302"),null,null);
        var input=new Command(1L,1L,100L,"10",List.of(one.entries().getFirst(),new Entry("302",raw2,secondBaseline)));
        assertEquals(2,service.apply(input).size());verify(devices,times(1)).validateSelection(100L,List.of(500L,501L));verify(records,times(2)).carryHistoricalRecord(any());
    }
    @Test void newerInstallationIsRetainedAndOldRecordEndsAtItsEffectiveDate() {
        var newer=copyForNewer();when(records.selectCurrentRecordForUpdate(any())).thenReturn(newer);
        String current=ORIGINAL.replace("\"700\"","\"800\"").replace("DPPMS-301","INSTALL-1")
                .replace("\"status\":\"0\"","\"status\":\"2\"")
                .replace("\"effective_from\":null","\"effective_from\":\"2022-01-01T00:00\"")
                .replace("\"install_time\":null","\"install_time\":\"2022-01-01T00:00\"");
        var result=service.apply(command(new Baseline(700L,ORIGINAL,800L,current))).getFirst();
        assertEquals("2022-01-01T00:00",JsonUtils.parseObject(result.after().formalFieldsJson(),Map.class).get("effective_to"));
        verifyNoInteractions(locations);assertNull(newer.getEffectiveTo());assertEquals(0L,newer.getVersion());
    }
    InstallationDO copyForNewer() {
        var row=JsonUtils.parseObject(JsonUtils.toJsonString(original),InstallationDO.class);row.setId(800L);row.setCode("INSTALL-1");
        row.setStatus(2);row.setInstallTime(LocalDateTime.of(2022,1,1,0,0));row.setEffectiveFrom(row.getInstallTime());return row;
    }
    @Test void laterAuditModificationBlocksInsteadOfBeingAdopted() {
        original.setUpdater("someone");assertThrows(IllegalArgumentException.class,()->service.apply(command(baseline())));
        verify(records,never()).carryHistoricalRecord(any());verifyNoInteractions(locations);
    }
    @Test void aCurrentLocationAppearingAfterPreviewBlocks() {
        when(records.selectCurrentRecordForUpdate(any())).thenReturn(copyForNewer());
        assertThrows(IllegalArgumentException.class,()->service.apply(command(baseline())));
        verify(records,never()).carryHistoricalRecord(any());verifyNoInteractions(locations);
    }
    @Test void tenantAndDeviceIdentityCannotBeBypassed() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.apply(command(baseline())));
        verifyNoInteractions(locations);TenantContextHolder.setTenantId(1L);
        when(devices.validateSelection(100L,List.of(500L))).thenReturn(List.of(new SelectedProjectDevice(500L,"OTHER",null,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.apply(command(baseline())));verify(records,never()).carryHistoricalRecord(any());
    }
    @Test void completeRawFieldsAndUnambiguousActorAreRequired() {
        var input=command(baseline());input.entries().getFirst().originalFields().remove("itemModel");
        assertThrows(IllegalArgumentException.class,()->service.apply(input));
        when(users.getHistoricalIdentitiesByUsername(any(),any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.apply(command(baseline())));
        verify(records,never()).carryHistoricalRecord(any());verifyNoInteractions(locations);
    }
}
