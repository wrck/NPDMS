package cn.iocoder.yudao.module.pms.engineering.controller.admin.training;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_STATUS_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingPdfControllerTest {
    @Test void rejectsVoidRecordsBeforeReadingEitherPrintLayout() {
        var service = mock(TrainingService.class);
        var controller = new TrainingPdfController();
        ReflectionTestUtils.setField(controller, "trainingService", service);
        var record = new TrainingDO();
        record.setStatus(3);
        when(service.getTraining(1L)).thenReturn(record);
        for (String snapshot : new String[]{null, "{\"engine\":\"FORM_CREATE_ELEMENT_PLUS\"}"}) {
            record.setPrintLayoutSnapshot(snapshot);
            var error = assertThrows(ServiceException.class, () -> controller.download(1L));
            assertEquals(TRAINING_STATUS_INVALID.getCode(), error.getCode());
            assertEquals("已作废的培训记录不允许下载 PDF", error.getMessage());
        }
    }
}
