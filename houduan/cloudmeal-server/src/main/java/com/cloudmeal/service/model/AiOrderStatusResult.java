package com.cloudmeal.service.model;

import com.cloudmeal.enums.AiOrderQueryOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户进行中订单的查询结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOrderStatusResult {

    private AiOrderQueryOutcome outcome;
    private AiOrderStatusItem selectedOrder;
    private List<AiOrderStatusItem> candidates;
    /** 是否还有未展示的较早进行中订单。 */
    private boolean hasMore;
}
