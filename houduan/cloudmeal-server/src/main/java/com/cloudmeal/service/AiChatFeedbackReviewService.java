package com.cloudmeal.service;

import com.cloudmeal.dto.AiChatFeedbackPageQueryDTO;
import com.cloudmeal.dto.AiFeedbackRetestSubmitDTO;
import com.cloudmeal.dto.AiFeedbackRetestReviewDTO;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.vo.AiFeedbackRetestSubmitVO;
import com.cloudmeal.vo.AiFeedbackRetestDetailVO;

/** 管理端AI客服未解决评价复核服务。 */
public interface AiChatFeedbackReviewService {

    /** 分页查询当前结果为“没解决”的评价。 */
    PageResult pageUnsolved(AiChatFeedbackPageQueryDTO queryDTO);

    /** 关联已同步知识并创建一个后台复测任务。 */
    AiFeedbackRetestSubmitVO submitRetest(
            Long feedbackId, AiFeedbackRetestSubmitDTO submitDTO);

    /** 查询该反馈最新一次复测的执行和人工确认状态。 */
    AiFeedbackRetestDetailVO getLatestRetest(Long feedbackId);

    /** 管理员确认复测回答正确或仍不正确。 */
    AiFeedbackRetestDetailVO reviewRetest(
            Long feedbackId, Long retestId,
            AiFeedbackRetestReviewDTO reviewDTO);
}
