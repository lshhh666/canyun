package com.cloudmeal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/** 管理端AI客服会话评价统计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatFeedbackStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** “有帮助”的评价数量。 */
    private Long helpfulCount;

    /** “没解决”的评价数量。 */
    private Long unsolvedCount;

    /** 评价总数。 */
    private Long totalCount;

    /** 有帮助数量占总评价数的百分比。 */
    private BigDecimal resolutionRate;
}
