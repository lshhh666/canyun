package com.cloudmeal.service;

import com.cloudmeal.vo.OrderPreviewVO;

public interface OrderPricingService {
    OrderPreviewVO preview(Long userId, Long addressBookId);
}
