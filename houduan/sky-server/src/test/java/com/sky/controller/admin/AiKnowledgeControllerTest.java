package com.sky.controller.admin;

import com.sky.dto.AiKnowledgeCreateDTO;
import com.sky.dto.AiKnowledgePageQueryDTO;
import com.sky.dto.AiKnowledgeUpdateDTO;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.result.PageResult;
import com.sky.service.AiKnowledgeService;
import com.sky.vo.AiKnowledgeSaveVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiKnowledgeControllerTest {

    private AiKnowledgeService aiKnowledgeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiKnowledgeService = Mockito.mock(AiKnowledgeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiKnowledgeController(aiKnowledgeService))
                .build();
    }

    @Test
    void shouldDelegateCreateFieldsAndReturnKnowledgeId() throws Exception {
        when(aiKnowledgeService.create(Mockito.any(AiKnowledgeCreateDTO.class)))
                .thenReturn(88L);

        mockMvc.perform(post("/admin/ai/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeKey\":\"SHOP_HOURS\","
                                + "\"title\":\"营业时间\","
                                + "\"category\":\"SHOP\","
                                + "\"content\":\"餐云每天09:00至21:00营业。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.knowledgeId").value(88))
                .andExpect(jsonPath("$.data.embeddingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.message").value("保存成功，向量同步中"));

        ArgumentCaptor<AiKnowledgeCreateDTO> captor =
                ArgumentCaptor.forClass(AiKnowledgeCreateDTO.class);
        verify(aiKnowledgeService).create(captor.capture());
        assertThat(captor.getValue().getKnowledgeKey()).isEqualTo("SHOP_HOURS");
        assertThat(captor.getValue().getTitle()).isEqualTo("营业时间");
        assertThat(captor.getValue().getCategory()).isEqualTo(AiKnowledgeCategory.SHOP);
        assertThat(captor.getValue().getContent())
                .isEqualTo("餐云每天09:00至21:00营业。");
    }

    @Test
    void shouldDelegateUpdateFieldsAndReturnNewVersionId() throws Exception {
        when(aiKnowledgeService.update(
                Mockito.eq(88L), Mockito.any(AiKnowledgeUpdateDTO.class)))
                .thenReturn(89L);

        mockMvc.perform(put("/admin/ai/knowledge/88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"营业时间\","
                                + "\"category\":\"SHOP\","
                                + "\"content\":\"餐云每天09:00至22:00营业。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.knowledgeId").value(89))
                .andExpect(jsonPath("$.data.embeddingStatus").value("PENDING"));

        ArgumentCaptor<AiKnowledgeUpdateDTO> captor =
                ArgumentCaptor.forClass(AiKnowledgeUpdateDTO.class);
        verify(aiKnowledgeService).update(Mockito.eq(88L), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("营业时间");
        assertThat(captor.getValue().getCategory()).isEqualTo(AiKnowledgeCategory.SHOP);
        assertThat(captor.getValue().getContent())
                .isEqualTo("餐云每天09:00至22:00营业。");
    }

    @Test
    void shouldDelegateKnowledgePageQuery() throws Exception {
        when(aiKnowledgeService.pageCurrent(Mockito.any(AiKnowledgePageQueryDTO.class)))
                .thenReturn(new PageResult(0L, Collections.emptyList()));

        mockMvc.perform(get("/admin/ai/knowledge/page")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("keyword", "营业")
                        .param("category", "SHOP")
                        .param("embeddingStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(0));

        ArgumentCaptor<AiKnowledgePageQueryDTO> captor =
                ArgumentCaptor.forClass(AiKnowledgePageQueryDTO.class);
        verify(aiKnowledgeService).pageCurrent(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getCategory()).isEqualTo(AiKnowledgeCategory.SHOP);
        assertThat(captor.getValue().getEmbeddingStatus())
                .isEqualTo(AiKnowledgeEmbeddingStatus.PENDING);
    }

    @Test
    void shouldDelegateManualEmbeddingRetryAndReturnItsState() throws Exception {
        when(aiKnowledgeService.retryEmbedding(88L)).thenReturn(
                AiKnowledgeSaveVO.builder()
                        .knowledgeId(88L)
                        .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                        .message("已提交，向量同步中")
                        .build());

        mockMvc.perform(post("/admin/ai/knowledge/88/embedding/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.knowledgeId").value(88))
                .andExpect(jsonPath("$.data.embeddingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.message").value("已提交，向量同步中"));

        verify(aiKnowledgeService).retryEmbedding(88L);
    }
}
