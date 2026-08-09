package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {
    //统计营业额
    Double getByMap(Map<String,Object> map);

    Integer getTotalUserBefore(LocalDateTime dateTimebegin1);


    Integer getinsertUser(Map<String, Object> map);

    Integer getorderNum(Map<String, Object> map);

    List<Map<String, String>> getTop10(LocalDateTime begin, LocalDateTime end);
}
