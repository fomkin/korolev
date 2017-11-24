package com.tenderowls.korolev.java;

import com.tenderowls.korolev.java.effects.Effect;
import com.tenderowls.korolev.java.effects.EffectsProvider;
import com.tenderowls.korolev.java.effects.ElementBinding;
import com.tenderowls.korolev.java.templateDsl.Dom;
import com.tenderowls.korolev.java.templateDsl.Html;
import korolev.Async;
import korolev.Context;
import levsha.Document.Node;
import scala.PartialFunction;
import scala.PartialFunction$;

import java.util.concurrent.CompletableFuture;

public abstract class Renderer<State, Message> extends Dom<State, Message> {

  private final Async<CompletableFuture> async;

  private final EffectsProvider<State, Message> effectsProvider =
    new EffectsProvider<>();

  protected final Html<State, Message> html = new Html<>();

  public Renderer(Async<CompletableFuture> async) {
    this.async = async;
  }

  abstract public Node<Effect<State, Message>> render(EffectsProvider<State, Message> effects, State state);

  protected  ElementBinding<State, Message> createElementBinding(String name) {
    return new ElementBinding<>(name, async);
  }

  public PartialFunction<State, Node<Context.Effect<CompletableFuture, State, Message>>> asScala() {
    return PartialFunction$.MODULE$.apply(state ->
      (Node<Context.Effect<CompletableFuture, State, Message>>) rc ->
        render(effectsProvider, state).apply(new ProxyRenderContext<>(rc, async))
    );
  }
}
