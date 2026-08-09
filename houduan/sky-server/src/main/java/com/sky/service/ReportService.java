package com.sky.service;


import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;


import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {
    //统计营业额数据
    TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end);
    //用户统计接口
    UserReportVO userStatistics(LocalDate begin, LocalDate end);
    //订单统计接口
    OrderReportVO ordersStatistics(LocalDate begin, LocalDate end);
    //查询销量排名top10接口
    SalesTop10ReportVO top10(LocalDate begin, LocalDate end);
    //到处运营数据报表
    void exportBusinessData(HttpServletResponse response);
}
