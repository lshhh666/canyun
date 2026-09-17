package com.cloudmeal.service;

import com.cloudmeal.dto.OrdersCancelDTO;
import com.cloudmeal.dto.OrdersConfirmDTO;
import com.cloudmeal.dto.OrdersPageQueryDTO;
import com.cloudmeal.dto.OrdersRejectionDTO;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.vo.OrderStatisticsVO;
import com.cloudmeal.vo.OrderVO;

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
