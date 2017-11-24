package com.tenderowls.korolev.java;

import com.tenderowls.korolev.java.effects.ElementBinding;
import korolev.Async;
import korolev.Context;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class BrowserAccess<State, Message> {

  private final Context.Access<CompletableFuture, State, Message> original;

  public BrowserAccess(Context.Access<CompletableFuture, State, Message> original) {
    this.original = original;
  }

  public CompletableFuture<Void> makeTransition(Function<State, State> transition) {
    return (CompletableFuture<Void>) original.transition(transition::apply);
  }

  public CompletableFuture<String> valueOf(ElementBinding<State, Message> binding) {
    return (CompletableFuture<String>) original.valueOf(binding.asScala());
  }
}
