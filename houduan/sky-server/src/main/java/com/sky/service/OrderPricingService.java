package com.sky.service;

import com.sky.vo.OrderPreviewVO;

public interface OrderPricingService {
    OrderPreviewVO preview(Long userId, Long addressBookId);
}
