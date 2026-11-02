package cn.iocoder.yudao.module.system.service.mail;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailTemplateDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class PasswordResetMailServiceTest {
 @Test void credentialsAreEncryptedAndAuthenticated() {
  var c=new MailCredentialCipher(); ReflectionTestUtils.setField(c,"key",Base64.getEncoder().encodeToString(new byte[32]));
  String encrypted=c.encrypt("test-only-secret"); assertFalse(encrypted.contains("test-only-secret"));
  assertEquals("test-only-secret",c.decrypt(encrypted)); assertEquals("legacy",c.decrypt("legacy"));
  assertThrows(IllegalStateException.class,()->c.decrypt(encrypted.substring(0,encrypted.length()-4)+"AAAA"));
 }
 @Test void templateAccountAndRedactedLogAreUsed() throws Exception {
  var s=spy(new PasswordResetMailService()); var t=mock(MailTemplateService.class); var a=mock(MailAccountService.class); var l=mock(MailLogService.class);
  ReflectionTestUtils.setField(s,"templates",t); ReflectionTestUtils.setField(s,"accounts",a); ReflectionTestUtils.setField(s,"logs",l);
  var template=new MailTemplateDO(); template.setAccountId(9L);template.setStatus(0);template.setParams(List.of("code"));template.setTitle("重置");template.setContent("验证码 {code}");
  var account=new MailAccountDO().setId(9L).setPassword("secret");
  when(t.getMailTemplateByCodeFromCache(PasswordResetMailService.TEMPLATE_CODE)).thenReturn(template);when(a.getMailAccountForSending(9L)).thenReturn(account);
  when(t.formatMailTemplateContent(anyString(),anyMap())).thenAnswer(i->((String)i.getArgument(0)).replace("{code}",String.valueOf(((Map<?,?>)i.getArgument(1)).get("code"))));
  when(l.createMailLog(anyLong(),anyInt(),anyCollection(),anyCollection(),anyCollection(),eq(account),eq(template),anyString(),anyMap(),eq(true))).thenReturn(42L);
  doNothing().when(s).deliver(any(),nullable(String.class),anyString(),anyString(),anyString());
  s.send(new PasswordResetMailService.Request(7L,"test@example.invalid","123456"));
  verify(l).createMailLog(eq(7L),anyInt(),anyCollection(),anyCollection(),anyCollection(),eq(account),eq(template),eq("验证码 [已隐藏]"),eq(Map.of("code","[已隐藏]")),eq(true));
  verify(s).deliver(eq(account),isNull(),eq("重置"),eq("验证码 123456"),eq("test@example.invalid"));verify(l).updateMailSendResult(42L,null,null);
 }
 @Test void missingTemplateNeverSendsOrCreatesLog() {
  var s=new PasswordResetMailService(); var t=mock(MailTemplateService.class); var l=mock(MailLogService.class);
  ReflectionTestUtils.setField(s,"templates",t); ReflectionTestUtils.setField(s,"logs",l);
  assertThrows(RuntimeException.class,()->s.send(new PasswordResetMailService.Request(7L,"test@example.invalid","123456")));
  verifyNoInteractions(l);
 }
 @Test void disabledTemplateRecordsIgnoredWithoutSending() throws Exception {
  var s=spy(new PasswordResetMailService()); var t=mock(MailTemplateService.class); var a=mock(MailAccountService.class); var l=mock(MailLogService.class);
  ReflectionTestUtils.setField(s,"templates",t); ReflectionTestUtils.setField(s,"accounts",a); ReflectionTestUtils.setField(s,"logs",l);
  var template=new MailTemplateDO(); template.setAccountId(9L); template.setStatus(1); template.setParams(List.of("code")); template.setContent("{code}");
  var account=new MailAccountDO().setId(9L).setPassword("secret");
  when(t.getMailTemplateByCodeFromCache(PasswordResetMailService.TEMPLATE_CODE)).thenReturn(template);
  when(a.getMailAccountForSending(9L)).thenReturn(account);
  when(t.formatMailTemplateContent(anyString(),anyMap())).thenReturn("[已隐藏]");
  assertThrows(RuntimeException.class,()->s.send(new PasswordResetMailService.Request(7L,"test@example.invalid","123456")));
  verify(l).createMailLog(eq(7L),anyInt(),anyCollection(),anyCollection(),anyCollection(),eq(account),eq(template),eq("[已隐藏]"),eq(Map.of("code","[已隐藏]")),eq(false));
  verify(s,never()).deliver(any(),nullable(String.class),anyString(),anyString(),anyString());
 }
 @Test void deliveryFailureRecordsSanitizedFailure() throws Exception {
  var s=spy(new PasswordResetMailService()); var t=mock(MailTemplateService.class); var a=mock(MailAccountService.class); var l=mock(MailLogService.class);
  ReflectionTestUtils.setField(s,"templates",t); ReflectionTestUtils.setField(s,"accounts",a); ReflectionTestUtils.setField(s,"logs",l);
  var template=new MailTemplateDO(); template.setAccountId(9L); template.setStatus(0); template.setParams(List.of("code")); template.setTitle("title"); template.setContent("{code}");
  var account=new MailAccountDO().setId(9L).setPassword("secret");
  when(t.getMailTemplateByCodeFromCache(PasswordResetMailService.TEMPLATE_CODE)).thenReturn(template); when(a.getMailAccountForSending(9L)).thenReturn(account);
  when(t.formatMailTemplateContent(anyString(),anyMap())).thenReturn("[已隐藏]");
  when(l.createMailLog(anyLong(),anyInt(),anyCollection(),anyCollection(),anyCollection(),eq(account),eq(template),anyString(),anyMap(),eq(true))).thenReturn(42L);
  doThrow(new IllegalStateException("123456")).when(s).deliver(any(),nullable(String.class),anyString(),anyString(),anyString());
  assertThrows(RuntimeException.class,()->s.send(new PasswordResetMailService.Request(7L,"test@example.invalid","123456")));
  var error=org.mockito.ArgumentCaptor.forClass(Exception.class); verify(l).updateMailSendResult(eq(42L),isNull(),error.capture());
  assertFalse(error.getValue().getMessage().contains("123456"));
 }
}
