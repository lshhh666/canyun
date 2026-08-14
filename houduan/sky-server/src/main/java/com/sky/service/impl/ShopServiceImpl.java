package com.sky.service.impl;


import com.sky.properties.BaiduMapProperties;
import com.sky.properties.ShopProperties;
import com.sky.service.ShopService;
import com.sky.vo.ShopInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopServiceImpl implements ShopService {
    private static final String KEY  = "shopStatus";
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ShopProperties shopProperties;
    @Autowired
    private BaiduMapProperties baiduMapProperties;
    @Override
    public void setShopStatus(Integer status) {
        redisTemplate.opsForValue().set(KEY, status);
    }

    @Override
    public Integer getShopStatus() {
        return (Integer) redisTemplate.opsForValue().get(KEY);
    }

    @Override
    public ShopInfoVO getShopInfo() {
        return ShopInfoVO.builder()
                .shopId(shopProperties.getId())
                .shopName(shopProperties.getName())
                .shopAddress(baiduMapProperties.getShopAddress())
                .phone(shopProperties.getPhone())
                .deliveryFee(shopProperties.getDeliveryFee())
                .status(getShopStatus())
                .build();
    }
}
