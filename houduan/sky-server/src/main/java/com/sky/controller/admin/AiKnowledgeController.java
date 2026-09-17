package com.sky.controller.admin;

import com.sky.context.BaseContext;
import com.sky.dto.AiKnowledgeCreateDTO;
import com.sky.dto.AiKnowledgePageQueryDTO;
import com.sky.dto.AiKnowledgeUpdateDTO;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AiKnowledgeService;
import com.sky.vo.AiKnowledgeSaveVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端AI客服权威知识接口。
 */
@RestController("adminAiKnowledgeController")
@RequestMapping("/admin/ai/knowledge")
@Api(tags = "管理端AI客服知识接口")
@Slf4j
@RequiredArgsConstructor
public class AiKnowledgeController {

    private static final String SYNCING_MESSAGE = "保存成功，向量同步中";

    private final AiKnowledgeService aiKnowledgeService;

    @PostMapping
    @ApiOperation("创建AI客服知识")
    public Result<AiKnowledgeSaveVO> create(@RequestBody AiKnowledgeCreateDTO dto) {
        log.info("创建AI客服知识，knowledgeKey={}",
                dto == null ? null : dto.getKnowledgeKey());
        return Result.success(syncingResult(aiKnowledgeService.create(dto)));
    }

    @GetMapping("/page")
    @ApiOperation("分页查询当前AI客服知识")
    public Result<PageResult> page(AiKnowledgePageQueryDTO queryDTO) {
        return Result.success(aiKnowledgeService.pageCurrent(queryDTO));
    }

    @PutMapping("/{id}")
    @ApiOperation("创建AI客服知识的新版本")
    public Result<AiKnowledgeSaveVO> update(
            @PathVariable Long id, @RequestBody AiKnowledgeUpdateDTO dto) {
        log.info("修改AI客服知识，sourceId={}", id);
        return Result.success(syncingResult(aiKnowledgeService.update(id, dto)));
    }

    @PostMapping("/{id}/embedding/retry")
    @ApiOperation("重新同步失败的AI客服知识向量")
    public Result<AiKnowledgeSaveVO> retryEmbedding(@PathVariable Long id) {
        log.info("人工重新同步AI客服知识向量，knowledgeId={}，operatorId={}",
                id, BaseContext.getCurrentId());
        return Result.success(aiKnowledgeService.retryEmbedding(id));
    }

    private AiKnowledgeSaveVO syncingResult(Long knowledgeId) {
        return AiKnowledgeSaveVO.builder()
                .knowledgeId(knowledgeId)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .message(SYNCING_MESSAGE)
                .build();
    }
}
