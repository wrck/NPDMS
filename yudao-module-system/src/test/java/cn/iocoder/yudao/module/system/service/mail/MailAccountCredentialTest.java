package cn.iocoder.yudao.module.system.service.mail;
import cn.iocoder.yudao.module.system.controller.admin.mail.vo.account.MailAccountSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.yudao.module.system.dal.mysql.mail.MailAccountMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MailAccountCredentialTest {
 @Test void storesOnlyCiphertextAndKeepsExistingPasswordOnEdit() {
  var cipher=new MailCredentialCipher(); ReflectionTestUtils.setField(cipher,"key",Base64.getEncoder().encodeToString(new byte[32]));
  var mapper=mock(MailAccountMapper.class); var service=new MailAccountServiceImpl();
  ReflectionTestUtils.setField(service,"mailAccountMapper",mapper); ReflectionTestUtils.setField(service,"credentialCipher",cipher);
  var create=new MailAccountSaveReqVO(); create.setPassword("test-password"); service.createMailAccount(create);
  var saved=ArgumentCaptor.forClass(MailAccountDO.class); verify(mapper).insert(saved.capture());
  assertNotEquals("test-password",saved.getValue().getPassword()); assertEquals("test-password",cipher.decrypt(saved.getValue().getPassword()));
  when(mapper.selectById(9L)).thenReturn(new MailAccountDO().setId(9L).setPassword(saved.getValue().getPassword()));
  var update=new MailAccountSaveReqVO(); update.setId(9L); update.setPassword(""); service.updateMailAccount(update);
  var changed=ArgumentCaptor.forClass(MailAccountDO.class); verify(mapper).updateById(changed.capture()); assertNull(changed.getValue().getPassword());
  assertEquals("test-password",service.getMailAccountForSending(9L).getPassword());
 }
}
