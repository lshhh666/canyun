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
import com.sky.enums.AiOrderCancellationOutcome;

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
    /** AI客服确认按钮专用：把正常状态竞争转换为确定性结果，不向用户暴露内部异常。 */
    AiOrderCancellationOutcome cancelPendingForAi(Long orderId, Long userId);
    //取消超时未支付订单
    void cancelTimeoutOrder(Long orderId);
    //再来一单
    void repetition(Long orderId);
    //催单
    void reminder(Long id);
}
