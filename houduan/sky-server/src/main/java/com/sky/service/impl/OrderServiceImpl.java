package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrderPreviewDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.enums.UserCouponStatus;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.CouponBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.service.OrderPricingService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderPreviewVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
        String jsonString = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(jsonString);
        return OrderPaymentVO.builder()
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime() != null
                        ? order.getEstimatedDeliveryTime().toString()
                        : null)
                .build();
    }

    @Override
    public OrderVO orderDetail(Long id) {
        OrderVO orderVO=new OrderVO();
        //查订单
        Orders orders=orderMapper.getById(id);
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
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
        // 先查订单，判断状态
        Orders order = orderMapper.getById(orderId);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getUserId(), BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.NO_PERMISSION);
        }
        // 当前项目没有退款链路，只允许用户取消待付款订单。
        // 已支付待接单的订单必须等退款能力完成后再开放取消。
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        int cancelledRows = orderMapper.cancelIfPending(orderId, now, null);
        if (cancelledRows != 1) {
            // 初次查询后支付可能已经抢先提交，条件更新失败时不能再释放优惠券。
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        releaseCouponIfLocked(order, now);
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
        Orders orders = orderMapper.getById(id);
        if(orders==null||!(orders.getStatus().equals(Orders.TO_BE_CONFIRMED))){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map=new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        long minutes = java.time.Duration.between(orders.getOrderTime(), LocalDateTime.now()).toMinutes();
        map.put("content","订单号:"+orders.getNumber()+"客户催单,已下单"+minutes+"分钟，仍未接单。");

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
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
