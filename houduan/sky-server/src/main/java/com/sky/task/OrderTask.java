package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
//定时任务类 处理订单状态
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    //处理超时订单
    @Scheduled(cron ="0 * * * * ? ")
    public void processTimeoutOrder(){
        log.info("定时任务开始{}",LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> orderList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        if(orderList!=null&&orderList.size()>0){
            for(Orders order:orderList){
                order.setStatus(Orders.CANCELLED);
                order.setCancelTime(LocalDateTime.now());
                order.setCancelReason(MessageConstant.ORDER_TIME_OUT);
                orderMapper.update(order);
            }
        }
    }
    //处理一直处于派送中的订单
    @Scheduled(cron = "0 0 1 * * ? ")
    public void processDeliveyOrder(){
        log.info("定时任务开始{}",LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if(ordersList!=null&&ordersList.size()>0){
            for(Orders order:ordersList){
                order.setStatus(Orders.COMPLETED);
                orderMapper.update(order);
            }
        }
    }
}
