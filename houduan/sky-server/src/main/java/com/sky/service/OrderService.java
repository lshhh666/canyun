package com.sky.service;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrderPreviewDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderPreviewVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
    OrderPreviewVO preview(OrderPreviewDTO orderPreviewDTO);

    OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO);

    OrderPaymentVO orderpayment(OrdersPaymentDTO ordersPaymentDTO);
    //查询订单详情
    OrderVO orderDetail(Long id);
    //历史订单查询
    PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO);
    //取消订单
    void cancelByOrderId(Long orderId);
    //再来一单
    void repetition(Long orderId);
    //催单
    void reminder(Long id);
}
