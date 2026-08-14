package com.sky.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.entity.AddressBook;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.properties.BaiduMapProperties;
import com.sky.properties.ShopProperties;
import com.sky.service.BaiduMapService;
import com.sky.service.OrderPricingService;
import com.sky.vo.OrderPreviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderPricingServiceImpl implements OrderPricingService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private BaiduMapService baiduMapService;
    @Autowired
    private BaiduMapProperties baiduMapProperties;
    @Autowired
    private ShopProperties shopProperties;
    private Clock clock = Clock.systemDefaultZone();

    @Override
    public OrderPreviewVO preview(Long userId, Long addressBookId) {
        List<ShoppingCart> cart = shoppingCartMapper.listShoppingCartByUserId(userId);
        if (cart == null || cart.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        AddressBook address = addressBookMapper.getById(addressBookId);
        if (address == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        if (!userId.equals(address.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_FAIL);
        }

        verifyDeliveryDistance(fullAddress(address));

        BigDecimal goods = BigDecimal.ZERO;
        int itemCount = 0;
        for (ShoppingCart item : cart) {
            if (item.getAmount() == null || item.getNumber() == null || item.getNumber() < 0) {
                throw new OrderBusinessException("购物车金额异常");
            }
            goods = goods.add(item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())));
            itemCount += item.getNumber();
        }
        BigDecimal pack = requiredMoney(shopProperties.getPackFeePerItem(), "打包费配置异常")
                .multiply(BigDecimal.valueOf(itemCount));
        BigDecimal delivery = requiredMoney(shopProperties.getDeliveryFee(), "配送费配置异常");
        int minutes = requiredPositive(shopProperties.getEstimatedDeliveryMinutes(), "预计送达时间配置异常");

        return OrderPreviewVO.builder()
                .goodsAmount(goods)
                .packAmount(pack)
                .deliveryFee(delivery)
                .totalAmount(goods.add(pack).add(delivery))
                .estimatedDeliveryTime(LocalDateTime.now(clock).plusMinutes(minutes))
                .build();
    }

    private void verifyDeliveryDistance(String userAddress) {
        try {
            String shopCoord = coordinate(baiduMapService.geocoder(baiduMapProperties.getShopAddress()),
                    "无法获取商家地址经纬度");
            String userCoord = coordinate(baiduMapService.geocoder(userAddress),
                    "无法获取收货地址经纬度，请检查地址是否正确");
            JSONObject direction = baiduMapService.direction(shopCoord, userCoord);
            JSONObject result = direction == null ? null : direction.getJSONObject("result");
            JSONArray routes = result == null ? null : result.getJSONArray("routes");
            if (routes == null || routes.isEmpty() || routes.getJSONObject(0) == null
                    || !routes.getJSONObject(0).containsKey("distance")) {
                throw new OrderBusinessException("无法计算配送距离");
            }
            int maxDistance = requiredPositive(shopProperties.getMaxDeliveryDistanceMeters(), "配送范围配置异常");
            if (routes.getJSONObject(0).getIntValue("distance") > maxDistance) {
                throw new OrderBusinessException(MessageConstant.ADDRESS_OUT_OF_DELIVERY_RANGE);
            }
        } catch (OrderBusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OrderBusinessException("配送距离计算失败，请稍后重试");
        }
    }

    private String coordinate(JSONObject response, String message) {
        JSONObject result = response == null ? null : response.getJSONObject("result");
        JSONObject location = result == null ? null : result.getJSONObject("location");
        if (location == null || location.getString("lat") == null || location.getString("lng") == null) {
            throw new OrderBusinessException(message);
        }
        return location.getString("lat") + "," + location.getString("lng");
    }

    private String fullAddress(AddressBook address) {
        return value(address.getProvinceName()) + value(address.getCityName())
                + value(address.getDistrictName()) + value(address.getDetail());
    }

    private String value(String value) { return value == null ? "" : value; }

    private BigDecimal requiredMoney(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new OrderBusinessException(message);
        return value;
    }

    private int requiredPositive(Integer value, String message) {
        if (value == null || value <= 0) throw new OrderBusinessException(message);
        return value;
    }
}
