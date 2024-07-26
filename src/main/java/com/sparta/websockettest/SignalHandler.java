package com.sparta.websockettest;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignalHandler implements WebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        sessions.put(session.getId(), session);

        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(message -> {
                    // Broadcast received message to all other sessions
                    Flux<WebSocketMessage> outboundFlux = Flux.fromIterable(sessions.values())
                            .filter(WebSocketSession::isOpen)
                            .filter(s -> !s.getId().equals(session.getId()))
                            .map(s -> s.textMessage(message));

                    return Flux.fromIterable(sessions.values())
                            .filter(WebSocketSession::isOpen)
                            .filter(s -> !s.getId().equals(session.getId()))
                            .flatMap(s -> s.send(outboundFlux))
                            .then();
                })
                .doFinally(signalType -> sessions.remove(session.getId()))
                .then();

        return input;
    }
}
