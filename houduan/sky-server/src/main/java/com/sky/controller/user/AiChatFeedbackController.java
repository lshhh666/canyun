package com.sky.controller.user;

import com.sky.dto.AiChatFeedbackDTO;
import com.sky.result.Result;
import com.sky.service.AiChatFeedbackService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/ai/session")
@RequiredArgsConstructor
@Api(tags = "用户端AI客服会话评价接口")
public class AiChatFeedbackController {

    private final AiChatFeedbackService feedbackService;

    @PostMapping("/feedback")
    @ApiOperation("评价已结束的AI客服会话")
    public Result<Void> submit(@RequestBody AiChatFeedbackDTO feedbackDTO) {
        feedbackService.submit(feedbackDTO);
        return Result.success();
    }
}
