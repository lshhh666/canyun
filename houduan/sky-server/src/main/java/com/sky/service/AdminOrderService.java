package com.sky.service;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;

public interface AdminOrderService {
    //订单搜索
    PageResult orderSearch(OrdersPageQueryDTO ordersPageQueryDTO);
    //各个状态的订单数量统计
    OrderStatisticsVO statistics();
    //取消订单
    void cancelOrder(OrdersCancelDTO ordersCancelDTO);
    //完成订单
    void completeOrder(Long id);
    //拒单
    void rejection(OrdersRejectionDTO ordersRejectionDTO);
    //接单
    void confirm(OrdersConfirmDTO ordersConfirmDTO);
    //派送订单
    void delivery(Long id);
    //查询订单详细
    OrderVO orderDetail(Long id);
}
