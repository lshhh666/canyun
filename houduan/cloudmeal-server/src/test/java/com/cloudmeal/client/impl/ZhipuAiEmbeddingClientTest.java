package com.cloudmeal.client.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.exception.AiServiceException;
import com.cloudmeal.properties.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ZhipuAiEmbeddingClientTest {

    private static final String EMBEDDINGS_URL = "https://example.com/v1/embeddings";
    private static final String TEST_API_KEY = "test-secret-key";

    private MockRestServiceServer server;
    private ZhipuAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("https://example.com/v1/");
        properties.setApiKey(TEST_API_KEY);
        properties.setEmbeddingModel("embedding-3");
        properties.setEmbeddingDimensions(256);
        client = new ZhipuAiEmbeddingClient(restTemplate, properties);
    }

    @Test
    void shouldSendCompleteTextAndExtractEmbedding() {
        server.expect(once(), requestTo(EMBEDDINGS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"model\":\"embedding-3\",\"input\":\"营业时间：餐云每天09:00至21:00营业。\",\"dimensions\":256}"))
                .andRespond(withSuccess(successResponse(256), MediaType.APPLICATION_JSON));

        List<Double> embedding = client.embed(" 营业时间：餐云每天09:00至21:00营业。 ");

        assertThat(embedding).hasSize(256);
        assertThat(embedding.get(0)).isEqualTo(0.25D);
        server.verify();
    }

    @Test
    void shouldRejectBlankTextBeforeCallingProvider() {
        assertThatThrownBy(() -> client.embed("  "))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldRejectEmptyProviderData() {
        server.expect(once(), requestTo(EMBEDDINGS_URL))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("晚上十点还能点餐吗"))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldRejectUnexpectedEmbeddingDimensions() {
        server.expect(once(), requestTo(EMBEDDINGS_URL))
                .andRespond(withSuccess(successResponse(2), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("晚上十点还能点餐吗"))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    private String successResponse(int dimensions) {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) {
                values.append(',');
            }
            values.append(i == 0 ? "0.25" : "0.0");
        }
        return "{\"data\":[{\"index\":0,\"embedding\":[" + values + "]}]}";
    }
}
