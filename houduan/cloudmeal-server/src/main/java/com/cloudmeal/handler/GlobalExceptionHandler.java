package com.cloudmeal.handler;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.exception.AiChatTurnException;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.result.Result;
import com.cloudmeal.vo.AiChatVO;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * AI客服本轮失败时，返回已创建的会话ID，便于前端继续该会话。
     */
    @ExceptionHandler(AiChatTurnException.class)
    public Result<AiChatVO> exceptionHandler(AiChatTurnException ex) {
        log.error("AI客服对话失败，sessionId={}", ex.getSessionId(), ex);
        AiChatVO data = AiChatVO.builder()
                .sessionId(ex.getSessionId())
                .build();
        return Result.error(ex.getMessage(), data);
    }

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }
    /**
     *处理sql异常
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        String message = ex.getMessage();
        if(message.contains("Duplicate entry")){
            String[] split = message.split(" ");
            String username=split[2];
            String msg=username+ MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }
}
