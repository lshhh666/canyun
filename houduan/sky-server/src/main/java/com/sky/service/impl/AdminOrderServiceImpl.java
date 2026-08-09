package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderdetailMapper;
import com.sky.result.PageResult;
import com.sky.service.AdminOrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AdminOrderServiceImpl implements AdminOrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderdetailMapper orderdetailMapper;

    @Override
    public OrderVO orderDetail(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        List<OrderDetail> orderDetailList = orderdetailMapper.getByOrderId(id);
        buildOrderDishes(orderDetailList, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;

    }
    // 拼接 orderDishes 字符串
    private static void buildOrderDishes(List<OrderDetail> orderDetailList, OrderVO orderVO) {
        StringBuilder sb = new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            if (sb.length() > 0) sb.append(",");
            sb.append(orderDetail.getName()).append("x").append(orderDetail.getNumber());
        }
        orderVO.setOrderDishes(sb.toString());
    }

    @Override
    public PageResult orderSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<OrderVO> orderVOPage=orderMapper.orderSearch(ordersPageQueryDTO);
        for(OrderVO orderVO : orderVOPage.getResult()){
            List<OrderDetail> orderDetails = orderdetailMapper.getByOrderId(orderVO.getId());
            buildOrderDishes(orderDetails, orderVO);
        }
        return new PageResult((orderVOPage.getTotal()),orderVOPage.getResult());
    }

    @Override
    public OrderStatisticsVO statistics() {
        List<Map<String, Object>> list=orderMapper.statistics();
        OrderStatisticsVO orderStatisticsVO=new OrderStatisticsVO();
        for(Map<String, Object> map : list){
            Integer status= ((Number) map.get("status")).intValue();
            Integer count=((Number) map.get("count")).intValue();
            switch (status) {
                case 2://待接单
                    orderStatisticsVO.setToBeConfirmed(count);
                    break;
                case 3: // 已接单（待派送）
                    orderStatisticsVO.setConfirmed(count);
                    break;
                case 4: // 派送中
                    orderStatisticsVO.setDeliveryInProgress(count);
                    break;
            }
        }
        return orderStatisticsVO;
    }

    @Override
    public void cancelOrder(OrdersCancelDTO ordersCancelDTO) {
        Orders orders=orderMapper.getById(ordersCancelDTO.getId());
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if(!orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders order=Orders.builder()
                        .id(ordersCancelDTO.getId())
                        .status(Orders.CANCELLED)
                        .cancelReason(ordersCancelDTO.getCancelReason())
                        .cancelTime(LocalDateTime.now())
                        .build();
        orderMapper.update(order);
    }

    @Override
    public void completeOrder(Long id) {
        Orders orders=orderMapper.getById(id);
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if(!orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders order=Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(order);
    }

    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orders=orderMapper.getById(ordersRejectionDTO.getId());
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if(!orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders order=Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .build();
             orderMapper.update(order);
    }

    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = orderMapper.getById(ordersConfirmDTO.getId());
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 只有待接单状态才能接单
        if (!orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders order = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(order);
    }

    @Override
    public void delivery(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 只有已接单状态才能派送
        if (!orders.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders order = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(order);
    }

}
