package com.cloudmeal.mapper;

import com.github.pagehelper.Page;
import com.cloudmeal.dto.OrdersCancelDTO;
import com.cloudmeal.dto.OrdersPageQueryDTO;
import com.cloudmeal.entity.Orders;
import com.cloudmeal.vo.OrderStatisticsVO;
import com.cloudmeal.vo.OrderVO;
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

    /**
     * 查询当前用户最近的进行中订单，只投影AI客服需要的非敏感字段。
     */
    List<Orders> listActiveByUserId(@Param("userId") Long userId);

    /** AI客服按订单主键和当前用户查询安全的订单详情字段。 */
    Orders getAiDetailByIdAndUserId(@Param("orderId") Long orderId,
                                    @Param("userId") Long userId);

    /** AI客服准备取消前只读取判断所需字段，并在SQL层校验订单归属。 */
    Orders getAiCancellationByIdAndUserId(@Param("orderId") Long orderId,
                                          @Param("userId") Long userId);

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
