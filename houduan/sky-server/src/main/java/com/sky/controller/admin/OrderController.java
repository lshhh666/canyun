package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AdminOrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Api("订单管理接口")
@RestController("adminOrderController")
@RequestMapping("/admin/order")
public class OrderController {
    @Autowired
    private AdminOrderService adminOrderService;
    //订单搜索
    @ApiOperation("订单搜索")
    @GetMapping("/conditionSearch")
    public Result<PageResult> orderSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("参数{}",ordersPageQueryDTO);
        PageResult pageResult=adminOrderService.orderSearch(ordersPageQueryDTO);
        return Result.success(pageResult);
    }
    @ApiOperation("各个状态的订单数量统计")
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO>statistics(){
        log.info("开始查询");
       OrderStatisticsVO orderStatisticsVO=adminOrderService.statistics();
       return Result.success(orderStatisticsVO);
    }
    @ApiOperation("取消订单")
    @PutMapping("/cancel")
    public Result cancelOrder(@RequestBody OrdersCancelDTO ordersCancelDTO){
        log.info("取消的{}",ordersCancelDTO);
        adminOrderService.cancelOrder(ordersCancelDTO);
        return Result.success();
    }
    @ApiOperation("完成订单")
    @PutMapping("/complete/{id}")
    public Result completeOrder(@PathVariable Long id){
        log.info("完成的订单id{}",id);
        adminOrderService.completeOrder(id);
        return Result.success();
    }
    @ApiOperation("拒单")
    @PutMapping("/rejection")
    public Result rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO){
        log.info("拒单{}",ordersRejectionDTO);
        adminOrderService.rejection(ordersRejectionDTO);
        return Result.success();
    }
    @ApiOperation("接单")
    @PutMapping("/confirm")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO){
        log.info("接单{}",ordersConfirmDTO);
        adminOrderService.confirm(ordersConfirmDTO);
        return Result.success();
    }
    @ApiOperation("查询订单详细")
    @GetMapping("/details/{id}")
    public Result<OrderVO> details(@PathVariable Long id){
        log.info("查询的订单id{}",id);
        OrderVO orderVO=adminOrderService.orderDetail(id);
        return Result.success(orderVO);
    }
    @ApiOperation("派送订单")
    @PutMapping("/delivery/{id}")
    public Result delivery(@PathVariable Long id){
        log.info("派送的订单id{}",id);
        adminOrderService.delivery(id);
        return Result.success();
    }

}
