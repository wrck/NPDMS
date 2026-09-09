package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSurveyResultDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationSurveyResultMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationSurveyResult;
import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationSurveyResultRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** PRE-02: typed persistence, cross-item boundary and immutable copy. */
@ExtendWith(MockitoExtension.class)
class PreparationSurveyResultServiceTest {
    @Mock PreparationSurveyResultMapper mapper;
    @InjectMocks PreparationSurveyResultService service;

    @Test
    void partialPatchPreservesOtherBusinessColumnsAndOldObject() {
        PreparationSurveyResultDO old = new PreparationSurveyResultDO();
        old.setPowerSupply("旧供电"); old.setPowerEnvironment("环境说明"); old.setCreator("7");
        when(mapper.selectByItem(any())).thenReturn(old);
        when(mapper.update(any())).thenReturn(1);
        PreparationSurveyResult patch = new PreparationSurveyResult(); patch.setPowerSupply(null);
        service.save(1L, 2L, 3L, "POWER", patch, 7L);
        verify(mapper).update(argThat(row -> row.getPowerSupply() == null
                && "环境说明".equals(row.getPowerEnvironment()) && row.getTenantId().equals(1L)
                && row.getPreparationId().equals(2L) && row.getItemId().equals(3L)));
        assertEquals("旧供电", old.getPowerSupply());
    }

    @Test
    void newDraftAllowsIncompleteValues() {
        when(mapper.insert(any())).thenReturn(1);
        service.save(1L, 2L, 3L, "POWER", new PreparationSurveyResult(), 7L);
        verify(mapper).insert(argThat(row -> row.getPowerSupply() == null && row.getItemId().equals(3L)));
    }

    @Test
    void copiesToNewIdentityWithoutMutatingOld() {
        PreparationSurveyResultDO old = new PreparationSurveyResultDO();
        old.setTenantId(1L); old.setPreparationId(2L); old.setItemId(3L);
        old.setOpticalModuleAvailable(false); old.setOriginalOpticalModule(false); old.setOpticalModule("旧模块");
        when(mapper.selectByItem(any())).thenReturn(old);
        when(mapper.insert(any())).thenReturn(1);
        service.copy(1L, 2L, 3L, 20L, 30L, 7L);
        verify(mapper).insert(argThat(row -> row.getPreparationId().equals(20L) && row.getItemId().equals(30L)
                && Boolean.FALSE.equals(row.getOpticalModuleAvailable()) && "旧模块".equals(row.getOpticalModule())));
        assertEquals(2L, old.getPreparationId()); assertEquals(3L, old.getItemId());
        verify(mapper, never()).update(any());
    }

    @Test
    void getExposesOnlyBusinessFieldsNotDatabaseIdentity() throws Exception {
        PreparationSurveyResultDO row = new PreparationSurveyResultDO(); row.setItemId(3L); row.setPowerSupply("供电");
        when(mapper.selectByItem(any())).thenReturn(row);
        String json = new ObjectMapper().writeValueAsString(service.get(1L, 2L, 3L));
        assertTrue(json.contains("powerSupply")); assertFalse(json.contains("itemId"));
        assertFalse(json.contains("submittedFields")); assertFalse(json.contains("tenantId"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"grounding", "constructionResource", "arbitrary"})
    void unknownMetadataAndArbitraryKeysAreRejectedEvenWhenNull(String field) {
        assertThrows(Exception.class, () -> new ObjectMapper().readValue("{\"" + field + "\":null}", PreparationSurveyResult.class));
    }

    @Test
    void crossItemNullAndOversizeDescriptionsAreRejected() {
        PreparationSurveyResult typed = new PreparationSurveyResult(); typed.setCabinet(null);
        assertThrows(RuntimeException.class, () -> PreparationSurveyResultRules.validatePatch("POWER", typed));
        PreparationSurveyResult longText = new PreparationSurveyResult(); longText.setFiber("a".repeat(1001));
        assertThrows(RuntimeException.class, () -> PreparationSurveyResultRules.validatePatch("FIBER", longText));
        assertThrows(RuntimeException.class, () -> PreparationSurveyResultRules.requireComplete("OPTICAL_MODULE", new PreparationSurveyResult()));
    }
}
