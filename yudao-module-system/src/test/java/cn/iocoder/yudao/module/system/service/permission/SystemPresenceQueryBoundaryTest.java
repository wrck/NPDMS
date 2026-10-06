package cn.iocoder.yudao.module.system.service.permission;
import cn.iocoder.yudao.module.system.service.dict.DictTypeServiceImpl;
import cn.iocoder.yudao.module.system.service.sms.SmsChannelServiceImpl;
import cn.iocoder.yudao.module.system.dal.mysql.dict.*;
import cn.iocoder.yudao.module.system.dal.mysql.sms.*;
import cn.iocoder.yudao.module.system.dal.dataobject.dict.DictTypeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsChannelDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SystemPresenceQueryBoundaryTest {
 @Test void referencedSmsChannelCannotBeDeletedIndividuallyOrInBatch(){
  var channel=mock(SmsChannelMapper.class);var templates=mock(SmsTemplateMapper.class);var service=new SmsChannelServiceImpl();
  ReflectionTestUtils.setField(service,"smsChannelMapper",channel);ReflectionTestUtils.setField(service,"smsTemplateMapper",templates);
  when(channel.selectById(9L)).thenReturn(new SmsChannelDO());when(templates.selectCountByChannelId(9L)).thenReturn(1L);
  assertThrows(RuntimeException.class,()->service.deleteSmsChannel(9L));assertThrows(RuntimeException.class,()->service.deleteSmsChannelList(List.of(9L)));
  verify(channel,never()).deleteById(9L);verify(channel,never()).deleteByIds(any());
 }
 @Test void unreferencedSmsChannelRetainsNormalDeletion(){
  var channel=mock(SmsChannelMapper.class);var templates=mock(SmsTemplateMapper.class);var service=new SmsChannelServiceImpl();
  ReflectionTestUtils.setField(service,"smsChannelMapper",channel);ReflectionTestUtils.setField(service,"smsTemplateMapper",templates);
  when(channel.selectById(9L)).thenReturn(new SmsChannelDO());when(templates.selectCountByChannelId(9L)).thenReturn(0L);
  service.deleteSmsChannel(9L);verify(channel).deleteById(9L);
 }
 @Test void referencedDictionaryTypeCannotBeDeletedIndividuallyOrInBatch(){
  var types=mock(DictTypeMapper.class);var data=mock(DictDataMapper.class);var service=new DictTypeServiceImpl();
  ReflectionTestUtils.setField(service,"dictTypeMapper",types);ReflectionTestUtils.setField(service,"dictDataMapper",data);
  var type=new DictTypeDO();type.setId(9L);type.setType("b12_fixture");when(types.selectById(9L)).thenReturn(type);when(types.selectByIds(List.of(9L))).thenReturn(List.of(type));when(data.selectCountByDictType("b12_fixture")).thenReturn(1L);
  assertThrows(RuntimeException.class,()->service.deleteDictType(9L));assertThrows(RuntimeException.class,()->service.deleteDictTypeList(List.of(9L)));verify(types,never()).updateToDelete(any(),any());
 }
}
