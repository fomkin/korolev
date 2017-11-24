package com.tenderowls.korolev.java.effects;

import korolev.Async;
import korolev.Context;

import java.util.concurrent.CompletableFuture;

public class ElementBinding<State, Message> implements Effect<State, Message> {

  private final Context.ElementId<CompletableFuture, State, Message> scalaElementId;

  public final String name;

  public ElementBinding(String name, Async<CompletableFuture> async) {
    this.name = name;
    this.scalaElementId = new Context.ElementId<>(async);
  }

  public Context.ElementId<CompletableFuture, State, Message> asScala() {
    return scalaElementId;
  }
}
