package com.sky.service.impl;

import com.sky.mapper.AiFeedbackRetestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFeedbackRetestPersistenceServiceImplTest {

    @Mock
    private AiFeedbackRetestMapper retestMapper;

    private AiFeedbackRetestPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiFeedbackRetestPersistenceServiceImpl(retestMapper);
    }

    @Test
    void shouldReturnWhetherPendingTaskWasClaimed() {
        LocalDateTime token = LocalDateTime.now().withNano(0);
        when(retestMapper.claimPendingRetest(11L, token)).thenReturn(1);

        assertThat(service.claim(11L, token)).isTrue();
        verify(retestMapper).claimPendingRetest(11L, token);
    }

    @Test
    void shouldRejectCompletionFromStaleWorker() {
        LocalDateTime token = LocalDateTime.now().withNano(0);
        LocalDateTime now = token.plusSeconds(5);
        when(retestMapper.markExecutionSucceeded(11L, token, "回答", "[31]", now))
                .thenReturn(0);

        assertThat(service.complete(11L, token, "回答", "[31]", now)).isFalse();
    }

    @Test
    void shouldMapRetryFailureResult() {
        LocalDateTime token = LocalDateTime.now().withNano(0);
        LocalDateTime now = token.plusSeconds(5);
        LocalDateTime nextRetryTime = now.plusMinutes(1);
        when(retestMapper.markRetryFailure(
                11L, token, "timeout", nextRetryTime, now)).thenReturn(1);

        assertThat(service.recordFailure(
                11L, token, "timeout", nextRetryTime, now)).isTrue();
    }
}
