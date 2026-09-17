package com.cloudmeal.controller.user;

import com.cloudmeal.dto.AiChatDTO;
import com.cloudmeal.result.Result;
import com.cloudmeal.service.AiChatService;
import com.cloudmeal.vo.AiChatVO;
import com.cloudmeal.vo.AiChatHistoryVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/ai")
@RequiredArgsConstructor
@Api(tags = "用户端AI客服接口")
public class AiChatController {

    private final AiChatService aiChatService;

    @GetMapping("/session/recent")
    @ApiOperation("恢复最近一次AI客服会话")
    public Result<AiChatHistoryVO> getRecentHistory() {
        return Result.success(aiChatService.getRecentHistory());
    }

    @PostMapping("/session/{sessionId}/close")
    @ApiOperation("结束当前AI客服会话并开始新对话")
    public Result<Void> startNewConversation(@PathVariable Long sessionId) {
        aiChatService.startNewConversation(sessionId);
        return Result.success();
    }

    @PostMapping("/chat")
    @ApiOperation("发送AI客服消息")
    public Result<AiChatVO> chat(@RequestBody AiChatDTO aiChatDTO) {
        return Result.success(aiChatService.chat(aiChatDTO.getMessage(), aiChatDTO.getSessionId()));
    }

    @PostMapping("/actions/{actionId}/confirm")
    @ApiOperation("确认执行AI客服待确认动作")
    public Result<AiChatVO> confirmAction(@PathVariable Long actionId) {
        return Result.success(aiChatService.confirmAction(actionId));
    }
}
