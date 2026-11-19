package org.messenger.testsupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;

/** Real raw STOMP connection through SockJS's /websocket endpoint. */
public final class StompTestClient implements AutoCloseable, WebSocket.Listener {
    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private final StringBuilder buffer = new StringBuilder();
    private final HttpClient http = HttpClient.newHttpClient();
    private final WebSocket socket;

    public StompTestClient(int port, String token) throws Exception {
        socket = http.newWebSocketBuilder().buildAsync(
                URI.create("ws://localhost:" + port + "/ws/websocket"), this)
                .get(10, TimeUnit.SECONDS);
        String authHeader = token == null ? "" : "Authorization:Bearer " + token + "\n";
        frame("CONNECT\naccept-version:1.2\nhost:localhost\n" + authHeader + "heart-beat:0,0\n\n");
        String connected = frames.poll(10, TimeUnit.SECONDS);
        if (connected == null || !connected.startsWith("CONNECTED")) {
            close();
            if (connected != null && connected.startsWith("ERROR")) throw new IllegalStateException("CONNECT rejected");
            throw new AssertionError("Missing STOMP response");
        }
    }

    @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        int end;
        while ((end = buffer.indexOf("\0")) >= 0) {
            String value = buffer.substring(0, end).stripLeading();
            buffer.delete(0, end + 1);
            if (!value.isBlank()) frames.add(value);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }
    public void subscribe() { subscribe("/user/queue/messages"); }
    public void subscribe(String destination) {
        frame("SUBSCRIBE\nid:" + destination + "\ndestination:" + destination + "\n\n");
    }
    public void send(String destination, String json) {
        send(destination, json, "");
    }
    public void send(String destination, String json, String extraHeaders) {
        frame("SEND\ndestination:" + destination + "\n" + extraHeaders + "content-type:application/json\n\n" + json);
    }
    private void frame(String value) { socket.sendText(value + '\0', true).join(); }
    public String receive(Duration timeout) throws Exception {
        String value = frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (value == null) throw new AssertionError("No STOMP message within " + timeout);
        if (!value.startsWith("MESSAGE")) throw new AssertionError(value);
        return value.substring(value.indexOf("\n\n") + 2);
    }
    @Override public void close() {
        socket.abort();
        http.close();
    }
}
