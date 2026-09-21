package com.cloudmeal.websocket;

import org.junit.jupiter.api.Test;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServerTest {

    @Test
    void shouldContinueBroadcastWhenOneSessionFails() throws Exception {
        WebSocketServer server = new WebSocketServer();
        Session failedSession = sessionThatThrows();
        Session healthySession = session();
        server.onOpen(failedSession, "failed-session");
        server.onOpen(healthySession, "healthy-session");

        assertDoesNotThrow(() -> server.sendToAllClient("message"));

        verify(healthySession.getBasicRemote()).sendText("message");
        server.onClose("failed-session");
        server.onClose("healthy-session");
    }

    @Test
    void shouldSupportConcurrentLifecycleAndBroadcast() throws Exception {
        WebSocketServer server = new WebSocketServer();
        int sessionCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < sessionCount; i++) {
            Session session = session();
            final int index = i;
            executor.submit(() -> {
                start.await();
                server.onOpen(session, "session-" + index);
                server.sendToAllClient("message");
                server.onClose("session-" + index);
                return null;
            });
        }

        start.countDown();
        executor.shutdown();
        assertDoesNotThrow(() -> {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent WebSocket operations did not finish");
            }
        });
    }

    private Session session() throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
        when(session.getBasicRemote()).thenReturn(basicRemote);
        return session;
    }

    private Session sessionThatThrows() throws Exception {
        Session session = session();
        RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
        doThrow(new RuntimeException("send failed"))
                .when(basicRemote)
                .sendText("message");
        return session;
    }
}
