package cn.iocoder.yudao.module.pms.integration.basesync.task;
import cn.iocoder.yudao.module.pms.integration.basesync.task.P25WarrantySyncTask.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class WarrantyBaseSyncTest {
    @Test void yearsConvertToMonthsWithoutRecomputingActualDates(){var s=new WarrantySyncSource();s.setWarrantyYears("3");s.setWarrantyStartDate("2020-02-29");s.setWarrantyEndDate("2021-06-07");s.setExtended("0");s.setIsLatest("1");var r=P25WarrantySyncTask.convert(s);assertEquals(36,r.getWarrantyMonths());assertEquals(LocalDate.of(2021,6,7),r.getWarrantyEndDate());assertEquals(false,r.getExtended());}
    @Test void nonMidnightSourceTimeCannotBeTruncated(){var s=new WarrantySyncSource();s.setWarrantyStartDate("2020-01-01 01:00:00");assertThrows(IllegalArgumentException.class,()->P25WarrantySyncTask.convert(s));}
    @Test void invertedDatesAreRejected(){var s=new WarrantySyncSource();s.setWarrantyStartDate("2022-01-01");s.setWarrantyEndDate("2021-01-01");assertThrows(IllegalArgumentException.class,()->P25WarrantySyncTask.convert(s));}
    @Test void undefinedLatestMarkerIsRejected(){var s=new WarrantySyncSource();s.setIsLatest("0");assertThrows(IllegalArgumentException.class,()->P25WarrantySyncTask.convert(s));}
}
