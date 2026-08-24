package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.*;

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

    /**
     * 查询尚未进入补偿流程的超时待付款订单。
     * 已有待处理、处理中或人工处理补偿任务的订单由补偿流程接管，原定时任务不再重复处理。
     */
    List<Orders> getTimeoutOrdersWithoutActiveCompensation(@Param("time") LocalDateTime time);

    Integer countByMap(Map map);

    Double sumByMap(Map map);

    int cancelIfPending(@Param("orderId") Long orderId,
                        @Param("cancelTime") LocalDateTime cancelTime,
                        @Param("cancelReason") String cancelReason);
    //修改支付状态
    int markPaidIfPending(@Param("orderId") Long orderId,
                          @Param("userId") Long userId,
                          @Param("payMethod") Integer payMethod,
                          @Param("checkoutTime") LocalDateTime checkoutTime);
}
