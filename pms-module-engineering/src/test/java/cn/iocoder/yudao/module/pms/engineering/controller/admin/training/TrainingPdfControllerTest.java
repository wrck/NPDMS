package cn.iocoder.yudao.module.pms.engineering.controller.admin.training;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TrainingPdfControllerTest {
 @Test void redirectsOnlyAfterNativeServiceRegisteredOrLocatedTheActualPdf() throws Exception {
  var service=mock(TrainingService.class);var controller=new TrainingPdfController();ReflectionTestUtils.setField(controller,"trainingService",service);
  when(service.requestPdfDownload(1L)).thenReturn("http://localhost/registered.pdf");
  var response=controller.download(1L);assertEquals(302,response.getStatusCode().value());assertEquals("http://localhost/registered.pdf",response.getHeaders().getLocation().toString());
  verify(service).requestPdfDownload(1L);verify(service,never()).getTraining(any());
 }
 @Test void aRejectedNativeGenerationNeverReturnsAnUnregisteredPdf() throws Exception {
  var service=mock(TrainingService.class);var controller=new TrainingPdfController();ReflectionTestUtils.setField(controller,"trainingService",service);
  when(service.requestPdfDownload(1L)).thenThrow(new IllegalStateException("owner denied"));
  assertThrows(IllegalStateException.class,()->controller.download(1L));
 }
}
