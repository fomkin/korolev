package com.tenderowls.korolev.java.effects;

import com.tenderowls.korolev.java.BrowserAccess;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class EventHandler<State, Message> implements Effect<State, Message> {

    public final String type;
    public final EventPhase phase;
    public final Function<BrowserAccess<State, Message>, CompletableFuture<Void>> action;

    public EventHandler(String type, EventPhase phase, Function<BrowserAccess<State, Message>, CompletableFuture<Void>> action) {
        this.type = type;
        this.phase = phase;
        this.action = action;
    }
}
