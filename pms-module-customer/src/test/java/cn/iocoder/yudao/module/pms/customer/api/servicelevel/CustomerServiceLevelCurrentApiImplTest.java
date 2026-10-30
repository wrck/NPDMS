package cn.iocoder.yudao.module.pms.customer.api.servicelevel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.servicelevel.CustomerServiceLevelDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.servicelevel.CustomerServiceLevelCurrentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelCurrentApi.*;
import static cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceLevelCurrentApiImplTest {
    private static final Query QUERY = new Query(1L, 20L);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    @Mock private CustomerServiceLevelCurrentMapper records;
    @Mock private CustomerMasterMapper customers;
    private CustomerServiceLevelCurrentApiImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new CustomerServiceLevelCurrentApiImpl(records, customers,
                Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void noRecordsReturnsUnconfiguredWithoutInventingSource() {
        customerExists();
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of());
        assertEquals(unconfigured(), service.inspect(QUERY));
    }

    @Test
    void oneRecordReturnsMaintenanceRecordIdVersionAndExactValues() {
        customerExists();
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(record()));
        assertEquals(available(), service.inspect(QUERY));
    }

    @Test
    void multipleCurrentRecordsAreCorruptionInsteadOfChoosingFirst() {
        customerExists();
        var other = record();
        other.setId(101L);
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(record(), other));
        assertEquals(OWNER_DATA_CORRUPTED,
                assertThrows(CustomerServiceLevelFactException.class, () -> service.inspect(QUERY)).getCode());
    }

    @Test
    void bothDateBoundariesIncludeTheWholeMaintenanceDate() {
        customerExists();
        var row = record();
        row.setValidFrom(TODAY);
        row.setValidTo(TODAY);
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(row));
        var fact = service.inspect(QUERY);
        assertEquals(Status.AVAILABLE, fact.status());
        assertEquals(TODAY, fact.validFrom());
        assertEquals(TODAY, fact.validTo());
    }

    @Test
    void nullDateBoundsRemainUnbounded() {
        customerExists();
        var row = record();
        row.setValidFrom(null);
        row.setValidTo(null);
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(row));
        var fact = service.inspect(QUERY);
        assertEquals(Status.AVAILABLE, fact.status());
        assertNull(fact.validFrom());
        assertNull(fact.validTo());
    }

    @Test
    void expiredFutureDraftDisabledAndArchivedRecordsDoNotParticipate() {
        customerExists();
        var expired = record();
        expired.setValidTo(TODAY.minusDays(1));
        var future = record();
        future.setValidFrom(TODAY.plusDays(1));
        var draft = record(); draft.setStatus(0);
        var disabled = record(); disabled.setStatus(2);
        var archived = record(); archived.setStatus(3);
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(expired, future, draft, disabled, archived));
        assertEquals(unconfigured(), service.inspect(QUERY));
    }

    @Test
    void reversedActiveDateRangeIsCorruption() {
        customerExists();
        var row = record();
        row.setValidFrom(TODAY.plusDays(1));
        row.setValidTo(TODAY.minusDays(1));
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(row));
        assertEquals(OWNER_DATA_CORRUPTED,
                assertThrows(CustomerServiceLevelFactException.class, () -> service.inspect(QUERY)).getCode());
    }

    @Test
    void missingVersionIsCorruption() {
        customerExists();
        var row = record(); row.setVersion(null);
        when(records.selectCustomerRecords(QUERY)).thenReturn(List.of(row));
        assertEquals(OWNER_DATA_CORRUPTED,
                assertThrows(CustomerServiceLevelFactException.class, () -> service.inspect(QUERY)).getCode());
    }

    @Test
    void mismatchingTenantIsRejectedBeforeAnyRead() {
        assertEquals(TENANT_CONTEXT_MISMATCH,
                assertThrows(CustomerServiceLevelFactException.class,
                        () -> service.inspect(new Query(2L, 20L))).getCode());
        verifyNoInteractions(records, customers);
    }

    @Test
    void customerFromAnotherTenantIsRejectedBeforeLevelRead() {
        var customer = new CustomerMasterDO(); customer.setTenantId(2L);
        when(customers.selectById(20L)).thenReturn(customer);
        assertEquals(CUSTOMER_NOT_FOUND,
                assertThrows(CustomerServiceLevelFactException.class, () -> service.inspect(QUERY)).getCode());
        verifyNoInteractions(records);
    }

    @Test
    void missingCustomerIsRejected() {
        when(customers.selectById(20L)).thenReturn(null);
        assertEquals(CUSTOMER_NOT_FOUND,
                assertThrows(CustomerServiceLevelFactException.class, () -> service.inspect(QUERY)).getCode());
        verifyNoInteractions(records);
    }

    @Test
    void invalidQueryIsRejectedWithoutReading() {
        assertEquals(INVALID_REQUEST,
                assertThrows(CustomerServiceLevelFactException.class, () -> service.inspect(null)).getCode());
        assertEquals(INVALID_REQUEST,
                assertThrows(CustomerServiceLevelFactException.class,
                        () -> service.inspect(new Query(1L, 0L))).getCode());
        verifyNoInteractions(records, customers);
    }

    private void customerExists() {
        var customer = new CustomerMasterDO(); customer.setId(20L); customer.setTenantId(1L);
        when(customers.selectById(20L)).thenReturn(customer);
    }

    private CustomerServiceLevelDO record() {
        var row = new CustomerServiceLevelDO();
        row.setId(100L); row.setTenantId(1L); row.setCustomerId(20L);
        row.setVersion(7L); row.setLevel("STANDARD"); row.setStatus(1);
        row.setValidFrom(TODAY.minusDays(1)); row.setValidTo(TODAY.plusDays(1));
        return row;
    }

    private Fact available() {
        return Fact.builder().status(Status.AVAILABLE).tenantId(1L).customerId(20L)
                .sourceRecordId(100L).sourceRecordVersion(7L).serviceLevelCode("STANDARD")
                .validFrom(TODAY.minusDays(1)).validTo(TODAY.plusDays(1)).build();
    }

    private Fact unconfigured() {
        return Fact.builder().status(Status.NOT_CONFIGURED).tenantId(1L).customerId(20L).build();
    }
}
