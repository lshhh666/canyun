package com.sky.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String ALREADY_EXISTS = "已存在";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String ADDRESS_BOOK_IS_FAIL="地址不属于当前用户,不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ADDRESS_OUT_OF_DELIVERY_RANGE = "超出配送范围，配送范围为5公里内";
    public static final String ORDER_TIME_OUT="订单超时，自动取消";
    public static final String NO_COUPONS_AVAILABLE="没有可用的优惠券";
    public static final String NO_PERMISSION="优惠券不属于该用户，无权使用";
    public static final String NOT_AVAILABLE="优惠券不可用";
    public static final String AI_SERVICE_UNAVAILABLE="AI客服暂时繁忙，请稍后再试";
    public static final String AI_MESSAGE_EMPTY="聊天内容不能为空";
    public static final String AI_MESSAGE_TOO_LONG="聊天内容不能超过100个字符";
    public static final String AI_CHAT_SESSION_UNAVAILABLE="会话不存在或无权访问";
    public static final String AI_CHAT_SESSION_CLOSED="会话已关闭";
    public static final String AI_CHAT_REPLY_PENDING="AI正在回答上一条消息，请稍后再试";
    public static final String AI_CHAT_SESSION_STATE_ERROR="会话状态异常";
    public static final String AI_CHAT_TURN_STALE="本轮请求已失效，请以最新提问为准";
    public static final String AI_CHAT_SESSION_SAVE_FAILED="会话保存失败";
    public static final String AI_CHAT_MESSAGE_SAVE_FAILED="聊天消息保存失败";
    public static final String AI_CHAT_ANSWER_EMPTY="AI客服回答不能为空";
    public static final String AI_CHAT_CONTEXT_LIMIT_INVALID="上下文消息数量必须在1到20之间";
    public static final String AI_CHAT_FEEDBACK_INVALID="会话评价参数不合法";
    public static final String AI_CHAT_FEEDBACK_SESSION_NOT_CLOSED="会话结束后才能评价";
    public static final String AI_CHAT_FEEDBACK_SAVE_FAILED="会话评价保存失败";
    public static final String AI_CHAT_FEEDBACK_DATE_RANGE_INVALID="评价日期范围不合法";
    public static final String AI_CHAT_FEEDBACK_PAGE_INVALID="评价分页参数不合法";
    public static final String AI_FEEDBACK_RETEST_INVALID="复测参数不合法";
    public static final String AI_FEEDBACK_RETEST_UNAVAILABLE="反馈不存在或不属于待处理的没解决评价";
    public static final String AI_FEEDBACK_RETEST_KNOWLEDGE_UNAVAILABLE="所选知识不存在、未启用或尚未同步完成";
    public static final String AI_FEEDBACK_RETEST_SAVE_FAILED="复测任务创建失败";
    public static final String AI_FEEDBACK_RETEST_QUEUED="已提交，等待系统复测";
    public static final String AI_FEEDBACK_RETEST_PROCESSING="正在复测";
    public static final String AI_FEEDBACK_RETEST_REVIEW_PENDING="复测已完成，等待人工确认";
    public static final String AI_FEEDBACK_ALREADY_HANDLED="该反馈已处理";
    public static final String AI_FEEDBACK_RETEST_NOT_FOUND="尚未找到该反馈的复测记录";
    public static final String AI_FEEDBACK_RETEST_REVIEW_INVALID="复测确认参数不合法";
    public static final String AI_FEEDBACK_RETEST_NOT_REVIEWABLE="复测尚未成功完成，暂时不能确认";
    public static final String AI_FEEDBACK_RETEST_ALREADY_REVIEWED="该复测结果已经确认";
    public static final String AI_FEEDBACK_RETEST_CONFIRMED_CORRECT="已确认回答正确，反馈已处理";
    public static final String AI_FEEDBACK_RETEST_CONFIRMED_INCORRECT="已记录回答仍不正确，请修改知识后重新复测";
    public static final String AI_FEEDBACK_RETEST_REVIEW_FAILED="复测确认保存失败";
    public static final String AI_KNOWLEDGE_INVALID="知识内容不合法";
    public static final String AI_KNOWLEDGE_ALREADY_EXISTS="知识标识已存在";
    public static final String AI_KNOWLEDGE_EMBEDDING_INVALID="知识向量无效";
    public static final String AI_KNOWLEDGE_SAVE_FAILED="知识保存失败";
    public static final String AI_KNOWLEDGE_UNAVAILABLE="知识不存在或已停用";
    public static final String AI_KNOWLEDGE_UPDATE_CONFLICT="知识状态已变化，请刷新后重试";
    public static final String AI_KNOWLEDGE_PAGE_INVALID="知识分页查询参数错误";
    public static final String AI_KNOWLEDGE_EMBEDDING_RETRY_CONFLICT="知识同步状态已变化，请刷新后重试";
    public static final String AI_KNOWLEDGE_EMBEDDING_RETRY_QUEUED="已提交，向量同步中";
    public static final String AI_KNOWLEDGE_EMBEDDING_PROCESSING="知识正在同步中";
    public static final String AI_KNOWLEDGE_EMBEDDING_READY="该知识已经可用";
    public static final String AI_PENDING_ACTION_UNAVAILABLE="待确认操作不存在或无权访问";
    public static final String AI_PENDING_ACTION_STATE_ERROR="待确认操作状态异常";
}
