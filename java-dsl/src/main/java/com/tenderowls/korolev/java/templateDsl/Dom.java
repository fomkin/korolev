package com.tenderowls.korolev.java.templateDsl;

import com.tenderowls.korolev.java.effects.Effect;
import levsha.Document;

import java.util.function.Function;

public class Dom<State, Message> {

  public Document.Node<Effect<State, Message>> text(String text) {
    return renderContext -> renderContext.addTextNode(text);
  }

  public <T extends Document.Node<Effect<State, Message>>> Document.Node<Effect<State, Message>> insertNodes
    (Iterable<T> values) {
    return renderContext -> {
      for (T value : values) {
        value.apply(renderContext);
      }
    };
  }

  public <T> Document.Node<Effect<State, Message>> renderValues(
    Iterable<T> values,
    Function<T, Document.Node<Effect<State, Message>>> renderer) {
    return renderContext -> {
      for (T value : values) {
        Document.Node<Effect<State, Message>> node = renderer.apply(value);
        node.apply(renderContext);
      }
    };
  }
}
