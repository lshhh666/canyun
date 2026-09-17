package com.cloudmeal.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.dto.OrdersPageQueryDTO;
import com.cloudmeal.dto.OrdersPaymentDTO;
import com.cloudmeal.dto.OrderPreviewDTO;
import com.cloudmeal.dto.OrdersSubmitDTO;
import com.cloudmeal.entity.*;
import com.cloudmeal.enums.UserCouponStatus;
import com.cloudmeal.enums.AiOrderCancellationOutcome;
import com.cloudmeal.exception.AddressBookBusinessException;
import com.cloudmeal.exception.CouponBusinessException;
import com.cloudmeal.exception.OrderBusinessException;
import com.cloudmeal.exception.ShoppingCartBusinessException;
import com.cloudmeal.mapper.*;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.service.OrderService;
import com.cloudmeal.service.OrderPricingService;
import com.cloudmeal.vo.OrderPaymentVO;
import com.cloudmeal.vo.OrderPreviewVO;
import com.cloudmeal.vo.OrderSubmitVO;
import com.cloudmeal.vo.OrderVO;
import com.cloudmeal.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderdetailMapper orderdetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private OrderPricingService orderPricingService;
    @Autowired
    private UserCouponMapper  userCouponMapper;

    @Override
    public OrderPreviewVO preview(OrderPreviewDTO orderPreviewDTO) {
        if (orderPreviewDTO == null || orderPreviewDTO.getAddressBookId() == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        return orderPricingService.preview(BaseContext.getCurrentId(), orderPreviewDTO.getAddressBookId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO) {

    //2. 计算订单原价、优惠金额和实付金额
    //3. 插入订单，获得 orderId
    //4. 原子锁券并检查返回值
        Long userId = BaseContext.getCurrentId();
        OrderPreviewVO quote = orderPricingService.preview(userId, ordersSubmitDTO.getAddressBookId());
        if (quote == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon = validateUserCoupon(ordersSubmitDTO.getUserCouponId(), BaseContext.getCurrentId(),quote.getGoodsAmount(), now);
        //查看购物车
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.listShoppingCartByUserId(userId);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //校验地址簿
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //生成订单号
        String orderNumber=generateOrderNumber();
        //订单金额只使用服务端报价，不信任客户端传入的金额字段
        if(quote.getTotalAmount() == null){
            throw new OrderBusinessException("金额异常");
        }
        //设置order的值
        Orders orders = new Orders();
        // Only copy fields the client is allowed to choose. Quote fields are server-owned,
        // and copying a missing Integer into an entity primitive causes an unboxing NPE.
        orders.setAddressBookId(ordersSubmitDTO.getAddressBookId());
        orders.setPayMethod(ordersSubmitDTO.getPayMethod());
        orders.setRemark(ordersSubmitDTO.getRemark());
        orders.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus());
        orders.setTablewareNumber(ordersSubmitDTO.getTablewareNumber() == null
                ? 0 : ordersSubmitDTO.getTablewareNumber());
        orders.setTablewareStatus(ordersSubmitDTO.getTablewareStatus());
        orders.setOriginalAmount(quote.getTotalAmount());
        orders.setGoodsAmount(quote.getGoodsAmount());
        orders.setDeliveryFee(quote.getDeliveryFee());
        //实际收钱
        if(userCoupon!=null){
            BigDecimal actualDiscount =
                    userCoupon.getDiscountAmount().min(quote.getTotalAmount());
            orders.setDiscountAmount(actualDiscount);
            orders.setAmount(quote.getTotalAmount().subtract(actualDiscount));
        }else{
            orders.setDiscountAmount(BigDecimal.ZERO);
            orders.setAmount(quote.getTotalAmount());
        }
        if (userCoupon != null) {
            orders.setUserCouponId(userCoupon.getId());
        }
        orders.setPackAmount(quote.getPackAmount().intValueExact());
        orders.setEstimatedDeliveryTime(quote.getEstimatedDeliveryTime());
        orders.setNumber(orderNumber);
        orders.setUserId(userId);
        orders.setStatus(Orders.PENDING_PAYMENT);           // 待付款
        orders.setOrderTime(now);           // 下单时间
        orders.setPayStatus(Orders.UN_PAID);                // 未支付
        orders.setAddress(addressBook.getProvinceName()     // 拼接完整地址
                + addressBook.getCityName()
                + addressBook.getDistrictName()
                + addressBook.getDetail());
        orders.setConsignee(addressBook.getConsignee());    // 收货人
        orders.setPhone(addressBook.getPhone());
        orderMapper.add(orders);
        if(userCoupon!=null){
            int i = userCouponMapper.lockForOrder(userCoupon.getId(), userId, orders.getId(), quote.getGoodsAmount(), now);
            if(i==0){
                throw  new CouponBusinessException("锁券失败");
            }
        }
        //设置Orderdetail
        for(ShoppingCart shoppingCart : shoppingCartList){
            OrderDetail  orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderdetailMapper.add(orderDetail);
        }
        //清空购物车
        shoppingCartMapper.deleteShoppingCart(userId);
       //创建vo
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderAmount(orders.getAmount())
                .orderNumber(orderNumber)
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public OrderPaymentVO orderpayment(OrdersPaymentDTO ordersPaymentDTO) {
        Orders order = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Long userId = BaseContext.getCurrentId();
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new OrderBusinessException(MessageConstant.NO_PERMISSION);
        }

        Integer status = order.getStatus();
        // Demo payment is completed by this endpoint. Retrying the same confirmed
        // payment must be idempotent because the client may lose the first response.
        if (Orders.TO_BE_CONFIRMED.equals(status) && Orders.PAID.equals(order.getPayStatus())) {
            return OrderPaymentVO.builder()
                    .estimatedDeliveryTime(order.getEstimatedDeliveryTime() != null
                            ? order.getEstimatedDeliveryTime().toString()
                            : null)
                    .build();
        }

        if (!Orders.PENDING_PAYMENT.equals(status)
                || !Orders.UN_PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        LocalDateTime now = LocalDateTime.now();
        int paidRows = orderMapper.markPaidIfPending(
                order.getId(), userId, ordersPaymentDTO.getPayMethod(), now);
        if (paidRows != 1) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        if (order.getUserCouponId() != null) {
            int usedRows = userCouponMapper.markUsedByOrder(
                    order.getUserCouponId(), order.getId(), now);
            if (usedRows != 1) {
                throw new CouponBusinessException(MessageConstant.ORDER_STATUS_ERROR);
            }
        }

        //通过websocket向客户端浏览器推送消息  type orderId content
        Map map=new HashMap();
        map.put("type",1);
        map.put("orderId",order.getId());
        map.put("content","订单号"+order.getNumber());
        sendAfterCommit(JSON.toJSONString(map));
        return OrderPaymentVO.builder()
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime() != null
                        ? order.getEstimatedDeliveryTime().toString()
                        : null)
                .build();
    }

    @Override
    public OrderVO orderDetail(Long id) {
        OrderVO orderVO=new OrderVO();
        // 用户只能查看自己的订单，避免通过猜测订单 ID 获取他人收货信息
        Orders orders = getCurrentUserOrder(id);
        //查订单详细
        List<OrderDetail> orderDetailList=orderdetailMapper.getByOrderId(id);
        //拼接OrderDishes
        StringBuilder sb = new StringBuilder();
        for(OrderDetail orderDetail : orderDetailList){
            if(sb.length() > 0) sb.append(",");
            sb.append(orderDetail.getName()).append("x").append(orderDetail.getNumber());
        }
        orderVO.setOrderDishes(sb.toString());
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<OrderVO> ordersPage=orderMapper.historyOrders(ordersPageQueryDTO);
        for(OrderVO orderVO : ordersPage.getResult()){
            List<OrderDetail> orderDetails = orderdetailMapper.getByOrderId(orderVO.getId());
            orderVO.setOrderDetailList(orderDetails);
            StringBuilder sb=new StringBuilder();
            for(OrderDetail orderDetail : orderDetails){
                if(sb.length() > 0) sb.append(",");
                sb.append(orderDetail.getName()).append("x").append(orderDetail.getNumber());
            }
            orderVO.setOrderDishes(sb.toString());
        }
        return new PageResult(ordersPage.getTotal(), ordersPage.getResult());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelByOrderId(Long orderId) {
        AiOrderCancellationOutcome outcome = cancelPendingForAi(
                orderId, BaseContext.getCurrentId());
        if (outcome == AiOrderCancellationOutcome.ORDER_UNAVAILABLE) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (outcome != AiOrderCancellationOutcome.CANCELLED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiOrderCancellationOutcome cancelPendingForAi(Long orderId, Long userId) {
        if (orderId == null || userId == null || userId <= 0) {
            return AiOrderCancellationOutcome.ORDER_UNAVAILABLE;
        }
        Orders order = orderMapper.getById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            return AiOrderCancellationOutcome.ORDER_UNAVAILABLE;
        }
        if (Orders.CANCELLED.equals(order.getStatus())) {
            return AiOrderCancellationOutcome.ALREADY_CANCELLED;
        }
        // 当前项目没有退款链路，只允许取消待付款且未支付的订单。
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())
                || !Orders.UN_PAID.equals(order.getPayStatus())) {
            return AiOrderCancellationOutcome.NOT_CANCELLABLE;
        }
        LocalDateTime now = LocalDateTime.now();
        int cancelledRows = orderMapper.cancelIfPending(orderId, now, null);
        if (cancelledRows == 1) {
            releaseCouponIfLocked(order, now);
            return AiOrderCancellationOutcome.CANCELLED;
        }
        // 初查后可能支付或被定时任务取消。重新读取，将并发结果转成正常业务结论。
        Orders latest = orderMapper.getById(orderId);
        if (latest == null || !Objects.equals(latest.getUserId(), userId)) {
            return AiOrderCancellationOutcome.ORDER_UNAVAILABLE;
        }
        if (Orders.CANCELLED.equals(latest.getStatus())) {
            return AiOrderCancellationOutcome.ALREADY_CANCELLED;
        }
        return AiOrderCancellationOutcome.NOT_CANCELLABLE;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelTimeoutOrder(Long orderId) {
        Orders order = orderMapper.getById(orderId);
        // The scheduler may have read the order just before a payment completed.
        // Re-check its current state inside this transaction before cancelling it.
        if (order == null || !Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int i = orderMapper.cancelIfPending(orderId, now, MessageConstant.ORDER_TIME_OUT);
        if(i!=1){
            return;
        }else {
            releaseCouponIfLocked(order, now);
        }
    }

    @Override
    public void repetition(Long orderId) {
        // 先校验订单归属，再读取明细，避免复制其他用户的订单内容
        getCurrentUserOrder(orderId);
        List<OrderDetail> orderDetails = orderdetailMapper.getByOrderId(orderId);
        List<ShoppingCart>shoppingCarts=new ArrayList<>();
        for (OrderDetail orderDetail : orderDetails) {
            ShoppingCart shoppingCart=new ShoppingCart();
            BeanUtils.copyProperties(orderDetail,shoppingCart);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setId(null);
            shoppingCarts.add(shoppingCart);
        }
        for (ShoppingCart shoppingCart : shoppingCarts) {
            shoppingCartMapper.addShoppingCart(shoppingCart);
        }
    }

    @Override
    public void reminder(Long id) {
        // 催单属于用户操作，必须同时满足订单归属和订单状态要求
        Orders orders = getCurrentUserOrder(id);
        if(!Orders.TO_BE_CONFIRMED.equals(orders.getStatus())){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map=new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        long minutes = java.time.Duration.between(orders.getOrderTime(), LocalDateTime.now()).toMinutes();
        map.put("content","订单号:"+orders.getNumber()+"客户催单,已下单"+minutes+"分钟，仍未接单。");

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 查询当前登录用户拥有的订单。
     *
     * 对“订单不存在”和“订单不属于当前用户”使用相同异常，既阻止水平越权，
     * 也避免向请求者泄露某个订单 ID 是否真实存在。
     */
    private Orders getCurrentUserOrder(Long orderId) {
        Orders order = orderMapper.getById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return order;
    }

    //生成订单号方法
    private String generateOrderNumber(){
        //时间戳
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        //随机数
       int random= new Random().nextInt(9000)+1000;
       return timestamp+random;
    }

    //1. 查询并初步校验用户优惠券

    private UserCoupon validateUserCoupon(Long userCouponId,Long userId,BigDecimal goodsAmount,LocalDateTime now){
        if (userCouponId == null) {
            return null;
        }
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new CouponBusinessException(MessageConstant.NO_COUPONS_AVAILABLE);
        }
        if(!Objects.equals(userCoupon.getUserId(),userId)){
            throw new CouponBusinessException(MessageConstant.NO_PERMISSION);
        }
        if(!(userCoupon.getStatus()== UserCouponStatus.AVAILABLE)||
                userCoupon.getValidStartTime().isAfter(now)||
                !userCoupon.getValidEndTime().isAfter(now)||
                goodsAmount.compareTo(userCoupon.getThresholdAmount()) < 0){
            throw new CouponBusinessException(MessageConstant.NOT_AVAILABLE);
        }
        return userCoupon;
    }

    /**
     * 订单状态提交成功后再发送通知，避免事务随后回滚却让管理端看到“新订单”。
     * WebSocket 是尽力而为的实时提示，发送失败不反向影响已经提交的支付结果。
     */
    private void sendAfterCommit(String message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            sendNotification(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendNotification(message);
                    }
                });
    }

    private void sendNotification(String message) {
        try {
            webSocketServer.sendToAllClient(message);
        } catch (RuntimeException ex) {
            log.warn("订单已提交，但 WebSocket 通知发送失败", ex);
        }
    }

    private void releaseCouponIfLocked(Orders order,LocalDateTime now){
        if(order.getUserCouponId()==null){
            return ;
        }
        int i = userCouponMapper.releaseByOrder(order.getUserCouponId(), order.getId(), now);
        if(i!=1){
            throw new CouponBusinessException("优惠券释放失败");
        }

    }
}
