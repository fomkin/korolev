package com.tenderowls.korolev.java.effects;

import com.tenderowls.korolev.java.BrowserAccess;
import levsha.Document;
import levsha.Document.Node;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class EffectsProvider<State, Message> {

  public Node<Effect<State, Message>> handleEvent(
      String type,
      EventPhase phase,
      Function<BrowserAccess<State, Message>, CompletableFuture<Void>> action) {

    return renderContext -> renderContext.addMisc(new EventHandler<>(type, phase, action));
  }

  public Node<Effect<State, Message>> handleEvent(
      String type,
      Function<BrowserAccess<State, Message>, CompletableFuture<Void>> action) {
    return handleEvent(type, EventPhase.Bubbling, action);
  }

  public Node<Effect<State, Message>> bind(ElementBinding<State, Message> binding) {
    return renderContext -> renderContext.addMisc(binding);
  }
}
