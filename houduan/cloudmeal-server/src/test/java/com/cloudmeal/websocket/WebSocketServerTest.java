package com.cloudmeal.websocket;

import org.junit.jupiter.api.Test;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServerTest {

    @Test
    void shouldContinueBroadcastWhenOneSessionFails() throws Exception {
        WebSocketServer failedServer = new WebSocketServer();
        WebSocketServer healthyServer = new WebSocketServer();
        Session failedSession = sessionThatThrows();
        Session healthySession = session();
        try {
            failedServer.onOpen(failedSession, "failed-session");
            healthyServer.onOpen(healthySession, "healthy-session");

            assertDoesNotThrow(() -> failedServer.sendToAllClient("message"));

            verify(healthySession.getBasicRemote()).sendText("message");
        } finally {
            failedServer.onClose("failed-session");
            healthyServer.onClose("healthy-session");
        }
    }

    @Test
    void shouldKeepNewSessionWhenOldSessionClosesAfterReconnect() throws Exception {
        WebSocketServer oldServer = new WebSocketServer();
        WebSocketServer newServer = new WebSocketServer();
        Session oldSession = session();
        Session newSession = session();
        try {
            oldServer.onOpen(oldSession, "reconnected-session");
            newServer.onOpen(newSession, "reconnected-session");

            oldServer.onClose("reconnected-session");
            newServer.sendToAllClient("message");

            verify(newSession.getBasicRemote()).sendText("message");
        } finally {
            oldServer.onClose("reconnected-session");
            newServer.onClose("reconnected-session");
        }
    }

    @Test
    void shouldSupportConcurrentLifecycleAndBroadcast() throws Exception {
        int sessionCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Session> sessions = new ArrayList<>();
        List<WebSocketServer> servers = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < sessionCount; i++) {
                Session session = session();
                sessions.add(session);
                WebSocketServer server = new WebSocketServer();
                servers.add(server);
                final int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    server.onOpen(session, "session-" + index);
                    server.sendToAllClient("message");
                    server.onClose("session-" + index);
                    return null;
                }));
            }

            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "concurrent WebSocket operations did not finish");
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
            for (int i = 0; i < sessions.size(); i++) {
                servers.get(i).onClose("session-" + i);
            }
        }
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
