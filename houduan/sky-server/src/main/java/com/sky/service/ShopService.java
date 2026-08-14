package com.sky.service;

import com.sky.vo.ShopInfoVO;

public interface ShopService {
    void setShopStatus(Integer status);

    Integer getShopStatus();

    ShopInfoVO getShopInfo();
}
