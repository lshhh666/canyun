package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    //插入
    void add(Orders orders);

    Orders getByNumber(String orderNumber);

    void update(Orders order);

    Orders getById(Long id);

    Page<OrderVO> historyOrders(OrdersPageQueryDTO ordersPageQueryDTO);


    Page<OrderVO> orderSearch(OrdersPageQueryDTO ordersPageQueryDTO);


    List<Map<String, Object>> statistics();

    //处理超时订单
    @Select("select * from orders where status=#{status} and  order_time <#{time}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime time);

    Integer countByMap(Map map);

    Double sumByMap(Map map);
}
