package com.sky.controller.user;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "C端订单接口")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> orderSubmit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("订单详细{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.orderSubmit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }
    @ApiOperation("订单支付")
    @PutMapping("/payment")
    public Result<OrderPaymentVO> orderpayment(@RequestBody OrdersPaymentDTO  ordersPaymentDTO) {
        log.info("订单的情况{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO=orderService.orderpayment(ordersPaymentDTO);
       return Result.success(orderPaymentVO);
    }
    @ApiOperation("查询订单详情")
    @GetMapping("/orderDetail/{id}")
    public Result<OrderVO> orderDetail(@PathVariable Long id){
            log.info("订单id{}",id);
            OrderVO orderVO=orderService.orderDetail(id);
            return Result.success(orderVO);
    }
    @ApiOperation("历史订单查询")
    @GetMapping("/historyOrders")
    public Result<PageResult> historyOrders(OrdersPageQueryDTO  ordersPageQueryDTO){
            log.info("查询的参数{}",ordersPageQueryDTO);
            PageResult orderVO=orderService.historyOrders(ordersPageQueryDTO);
            return Result.success(orderVO);
    }
    @ApiOperation("取消订单")
    @PutMapping("/cancel/{id}")
    public Result cancelByOrderId(@PathVariable("id") Long orderId){
            log.info("订单id{}",orderId);
            orderService.cancelByOrderId(orderId);
            return Result.success();
    }
    @ApiOperation("再来一单")
    @PostMapping("/repetition/{id}")
    public Result repetition(@PathVariable("id") Long orderId){
        log.info("再来一单的订单id{}",orderId);
        orderService.repetition(orderId);
        return Result.success();
    }
    @ApiOperation("用户催单")
    @GetMapping("/reminder/{id}")
    public Result reminder(@PathVariable Long id){
        log.info("催单的订单id{}",id);
        orderService.reminder(id);
        return Result.success();
    }
}
