package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.ReportMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private WorkspaceService workspaceService;
    @Override
    public TurnoverReportVO turnoverStatistics( LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getLocalDates(begin, end);
        List<Double> turnoverList=new ArrayList<>();
        for (LocalDate date : dateList) {
            Map<String,Object> map = buildDateMap(date);
            map.put("status", Orders.COMPLETED);
            Double turnover = reportMapper.getByMap(map);
            turnover= turnover==null?0.0:turnover;
            turnoverList.add(turnover);
        }
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }


    @Override
    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
        LocalDateTime dateTimebegin1 = LocalDateTime.of(begin, LocalTime.MIN);
        List<LocalDate> dateList = getLocalDates(begin, end);
        List<Integer> userNumList=new ArrayList<>();
        List<Integer> insertUserList = new ArrayList<>();
        Integer baseNum = reportMapper.getTotalUserBefore(dateTimebegin1);
        baseNum=nullToZero(baseNum);
        for (LocalDate localDate : dateList) {
            Map<String,Object> map = buildDateMap(localDate);
            Integer insertusernum= nullToZero(reportMapper.getinsertUser(map));
            baseNum+=insertusernum;
            insertUserList.add(insertusernum);
            userNumList.add(baseNum);
        }
        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .newUserList(StringUtils.join(insertUserList,","))
                .totalUserList(StringUtils.join(userNumList,","))
                .build();
    }

    @Override
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getLocalDates(begin, end);
        //每日订单数，以逗号分隔
        List<Integer> orderNumList=new ArrayList<>();
        //每日有效订单数，以逗号分隔，例如：20,21,10
        List<Integer> validOrderCountList=new ArrayList<>();
        Map<String,Object> allmap = new HashMap();
        allmap.put("begin",LocalDateTime.of(begin, LocalTime.MIN));
        allmap.put("end",LocalDateTime.of(end, LocalTime.MAX));
        //有效订单数
        allmap.put("status", Orders.COMPLETED);
        Integer validOrderCountall=nullToZero(reportMapper.getorderNum(allmap));
        allmap.remove("status",Orders.COMPLETED);
        Integer totalOrderCount=nullToZero(reportMapper.getorderNum(allmap));
        //订单完成率
        Double orderCompletionRate = totalOrderCount == 0 ? 0.0 : (double) validOrderCountall / totalOrderCount;

        for (LocalDate date : dateList) {
            Map<String,Object> map = buildDateMap(date);
            map.put("status", Orders.COMPLETED);
            Integer validOrderCount=nullToZero(reportMapper.getorderNum(map));
            validOrderCountList.add(validOrderCount);
            map.remove("status",Orders.COMPLETED);
            Integer orderNum=nullToZero(reportMapper.getorderNum(map));
            orderNumList.add(orderNum);
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderNumList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCountall)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    @Override
    public SalesTop10ReportVO top10(LocalDate begin, LocalDate end) {
        LocalDateTime dateTimeBegin = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime dateTimeend = LocalDateTime.of(end, LocalTime.MAX);
        List<Map<String,String>> top10=reportMapper.getTop10(dateTimeBegin, dateTimeend);
        //商品名称列表
        List<String> nameList=new ArrayList<>();
        //销量列表
        List<String> numberList=new ArrayList<>();
        for (Map<String, String> stringIntegerMap : top10) {
            nameList.add(String.valueOf(stringIntegerMap.get("name")));
            numberList.add(String.valueOf(stringIntegerMap.get("number")));
        }
        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList,","))
                .numberList(StringUtils.join(numberList,","))
                .build();
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
            //查数据库 获得数据
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN),
                LocalDateTime.of(dateEnd, LocalTime.MAX));
        //通过POI将数据写入Excel文件
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        if (in == null) {
            throw new RuntimeException("模板文件不存在");
        }

        try {
            //基于模板文件创建新的Excel文件
            XSSFWorkbook excel = new XSSFWorkbook(in);
            //填充数据
            //获取Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");
            //时间段
            sheet.getRow(1).getCell(1).setCellValue("时间:"+dateBegin+"至"+dateEnd);
            //第4行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());
            //第5行
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //明细数据
            for (int i=0;i<30;i++){
                LocalDate date = dateBegin.plusDays(i);
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                row = sheet.getRow(i + 7);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }
            //通过输出流将Excel文件下载到客户端
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);

            outputStream.close();
            excel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private static List<LocalDate> getLocalDates(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList=new ArrayList<>();
        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        return dateList;
    }

    private Map<String,Object> buildDateMap(LocalDate date) {
        Map<String,Object> map = new HashMap();
        map.put("begin", LocalDateTime.of(date, LocalTime.MIN));
        map.put("end", LocalDateTime.of(date, LocalTime.MAX));
        return map;
    }

    private int nullToZero(Integer val) {
        return val == null ? 0 : val;
    }

}
