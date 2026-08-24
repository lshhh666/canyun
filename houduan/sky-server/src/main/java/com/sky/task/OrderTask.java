package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.OrderCompensationService;
import com.sky.service.OrderService;
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
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderCompensationService orderCompensationService;
    //处理超时订单
    @Scheduled(cron ="0 * * * * ? ")
    public void processTimeoutOrder(){
        log.info("定时任务开始{}",LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        // 已进入补偿流程的订单由补偿任务接管，避免绕过5分钟间隔和最大重试次数
        List<Orders> orderList = orderMapper.getTimeoutOrdersWithoutActiveCompensation(time);
        if(orderList!=null&&orderList.size()>0){
            for(Orders order:orderList){
                try {
                    orderService.cancelTimeoutOrder(order.getId());
                } catch (RuntimeException ex) {
                    log.error("取消超时订单失败，orderId={}, userCouponId={}",
                            order.getId(), order.getUserCouponId(), ex);
                    try {
                        orderCompensationService.recordTimeoutCancelFailure(order.getId(),order.getUserCouponId(),ex.toString());
                    } catch (RuntimeException recordEx) {
                        // 补偿记录失败不能影响后续订单，但必须输出严重日志
                        log.error("保存订单补偿记录失败，orderId={}, userCouponId={}",
                                order.getId(), order.getUserCouponId(), recordEx);
                    }
                    // 单个订单失败后继续处理后续订单；取消事务已回滚，该订单仍保持待付款。
                }
            }
        }
    }

    /** 每分钟错开30秒处理一批到期补偿任务，避免与首次超时扫描同时启动。 */
    @Scheduled(cron = "30 * * * * ?")
    public void processOrderCompensation() {
        orderCompensationService.processDueTasks();
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
