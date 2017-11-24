package com.tenderowls.korolev.java;

import com.tenderowls.korolev.java.effects.Effect;
import com.tenderowls.korolev.java.effects.ElementBinding;
import com.tenderowls.korolev.java.effects.EventHandler;
import korolev.Async;
import korolev.Context;
import korolev.EventResult;
import levsha.RenderContext;
import levsha.XmlNs;
import scala.Symbol;

import java.util.concurrent.CompletableFuture;

public class ProxyRenderContext<State, Message> implements RenderContext<Effect<State, Message>> {

  private final Async<CompletableFuture> async;

  private final RenderContext<Context.Effect<CompletableFuture, State, Message>> original;

  public ProxyRenderContext(
      RenderContext<Context.Effect<CompletableFuture, State, Message>> original,
      Async<CompletableFuture> async) {
    this.async = async;
    this.original = original;
  }

  @Override
  public void openNode(XmlNs xmlns, String name) {
    original.openNode(xmlns, name);
  }

  @Override
  public void closeNode(String name) {
    original.closeNode(name);
  }

  @Override
  public void setAttr(XmlNs xmlNs, String name, String value) {
    original.setAttr(xmlNs, name, value);
  }

  @Override
  public void addTextNode(String text) {
    original.addTextNode(text);
  }

  @Override
  public void addMisc(Effect<State, Message> effect) {
    if (effect instanceof EventHandler) {
      System.out.println("Adding event handler " + effect);
      EventHandler<State, Message> javaEvent = (EventHandler<State, Message>) effect;
      Context.Event<CompletableFuture, State, Message> scalaEvent = new Context.Event<>(
        Symbol.apply(javaEvent.type),
        Converters.eventPhase(javaEvent.phase),
        scalaAccess -> new EventResult<>(
          javaEvent.action.apply(new BrowserAccess<>(scalaAccess)),
          false,
          async
        ),
        async
      );
      System.out.println("Scala effect " + scalaEvent);
      original.addMisc(scalaEvent);
    } else if (effect instanceof ElementBinding) {
      ElementBinding<State, Message> javaBinding = (ElementBinding<State, Message>) effect;
      original.addMisc(javaBinding.asScala());
    } else {
      System.out.println("Unknown effect: " + effect);
    }
  }
}
