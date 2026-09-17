package com.cloudmeal.service;

import com.cloudmeal.vo.ShopInfoVO;

public interface ShopService {
    void setShopStatus(Integer status);

    Integer getShopStatus();

    ShopInfoVO getShopInfo();
}
