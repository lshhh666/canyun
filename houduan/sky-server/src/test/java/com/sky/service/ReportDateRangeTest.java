package com.sky.service;

import com.sky.controller.admin.ReportController;
import com.sky.exception.BaseException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.mapper.ReportMapper;
import com.sky.service.impl.ReportServiceImpl;
import com.sky.vo.TurnoverReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportDateRangeTest {
    private ReportServiceImpl service;
    private ReportMapper mapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(ReportMapper.class);
        service = new ReportServiceImpl();
        ReflectionTestUtils.setField(service, "reportMapper", mapper);
        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"turnoverStatistics", "userStatistics", "ordersStatistics", "top10"})
    void rejectsNullReversedAndOversizedRangesBeforeDatabaseAccess(String operation) {
        LocalDate day = LocalDate.of(2026, 9, 13);
        assertThrows(BaseException.class, () -> query(operation, null, day));
        assertThrows(BaseException.class, () -> query(operation, day, null));
        assertThrows(BaseException.class, () -> query(operation, day, day.minusDays(1)));
        assertThrows(BaseException.class, () -> query(operation, day, day.plusDays(366)));
        assertThrows(BaseException.class, () -> query(operation, LocalDate.MIN, LocalDate.MAX));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"turnoverStatistics", "userStatistics", "ordersStatistics", "top10"})
    void dateErrorsReturnReadableBusinessResponse(String operation) throws Exception {
        mvc.perform(get("/admin/report/" + operation)
                        .param("begin", "2026-09-13").param("end", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请提供有效的起止日期，开始日期不能晚于结束日期"));
        mvc.perform(get("/admin/report/" + operation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"turnoverStatistics", "userStatistics", "ordersStatistics", "top10"})
    void acceptsSingleDayAndInclusiveLeapYearLimit(String operation) {
        assertNotNull(query(operation, LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 29)));
        assertNotNull(query(operation, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)));
        assertNotNull(query(operation, LocalDate.MAX, LocalDate.MAX));
    }

    @Test
    void dateSeriesIncludesBothEndpointsAndZeroFillsMissingData() {
        TurnoverReportVO report = service.turnoverStatistics(
                LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1));
        assertEquals("2024-02-28,2024-02-29,2024-03-01", report.getDateList());
        assertEquals("0.0,0.0,0.0", report.getTurnoverList());
        verify(mapper, times(3)).getByMap(anyMap());
    }

    private Object query(String operation, LocalDate begin, LocalDate end) {
        switch (operation) {
            case "turnoverStatistics": return service.turnoverStatistics(begin, end);
            case "userStatistics": return service.userStatistics(begin, end);
            case "ordersStatistics": return service.ordersStatistics(begin, end);
            case "top10": return service.top10(begin, end);
            default: throw new IllegalArgumentException(operation);
        }
    }
}
