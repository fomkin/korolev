package com.tenderowls.korolev.java;

import korolev.Async;
import korolev.server.KorolevServiceConfig;
import korolev.server.ServerRouter;
import korolev.state.StateSerializer;
import korolev.state.StateStorage;
import scala.PartialFunction$;
import scala.runtime.BoxedUnit;
import scala.collection.JavaConverters;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public abstract class Service<State, Message> {

  private final StateSerializer<State> serializer;

  protected final Async<CompletableFuture> async;

  public Service(StateSerializer<State> serializer, Async<CompletableFuture> async) {
    this.serializer = serializer;
    this.async = async;
  }

  public abstract CompletableFuture<State> getDefaultState(String deviceId);

  public abstract Renderer<State, Message> getRenderer();

  public KorolevServiceConfig<CompletableFuture, State, Message> asScala() {
    StateStorage<CompletableFuture, State> stateStorage = StateStorage.forDeviceId(
      this::getDefaultState,
      async,
      serializer
    );
    return new KorolevServiceConfig<CompletableFuture, State, Message>(
      stateStorage,
      ServerRouter.empty(async),
      getRenderer().asScala(),
      JavaConverters.asScalaBuffer(new ArrayList<>()),
      KorolevServiceConfig.defaultConnectionLostWidget(),
      1024 * 1024 * 8,
      KorolevServiceConfig.defaultEnvironmentConfigurator(),
      async
    );
  }
}
