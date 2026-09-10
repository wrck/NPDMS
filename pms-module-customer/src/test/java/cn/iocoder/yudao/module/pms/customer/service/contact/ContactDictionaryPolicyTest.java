package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactDictionaryPolicyTest {
    private ContactValues value(String title, String role) {
        return new ContactValues("测试联系人",null,title,"13800138000",null,null,role,null);
    }
    @Test void changedSelectionsUseEnabledPlatformDictionaryValues() {
        var api = mock(DictDataApi.class); var policy = new ContactDictionaryPolicy(api);
        policy.validateChanges(value("TITLE_A", "ROLE_A"), null);
        verify(api).validateDictDataList(ContactDictionaryPolicy.TITLE, List.of("TITLE_A"));
        verify(api).validateDictDataList(ContactDictionaryPolicy.CUSTOMER_ROLE, List.of("ROLE_A"));
        doThrow(new IllegalArgumentException("disabled")).when(api).validateDictDataList(ContactDictionaryPolicy.TITLE,List.of("DISABLED"));
        assertThrows(IllegalArgumentException.class, () -> policy.validateChanges(value("DISABLED",null),null));
    }
    @Test void unchangedHistoricalValuesAndClearingAreNotSilentlyRewrittenOrRejected() {
        var api = mock(DictDataApi.class); var policy = new ContactDictionaryPolicy(api);
        policy.validateChanges(value("历史职务","历史角色"),value("历史职务","历史角色"));
        policy.validateChanges(value(null,null),value("历史职务","历史角色"));
        verifyNoInteractions(api);
    }
}
