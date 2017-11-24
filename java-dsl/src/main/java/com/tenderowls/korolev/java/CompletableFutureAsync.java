package com.tenderowls.korolev.java;

import korolev.Async;
import scala.Function0;
import scala.Function1;
import scala.Option;
import scala.PartialFunction;
import scala.collection.TraversableOnce;
import scala.collection.generic.CanBuildFrom;
import scala.collection.mutable.Builder;
import scala.runtime.BoxedUnit;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletableFutureAsync implements Async<CompletableFuture> {

  private final ExecutorService executor;

  public CompletableFutureAsync(ExecutorService executor) {
    this.executor = executor;
  }

  public <A> CompletableFuture pureStrict(A value) {
    return CompletableFuture.completedFuture(value);
  }

  @Override
  public <A> CompletableFuture pure(Function0<A> value) {
    return CompletableFuture.completedFuture(value.apply());
  }

  @Override
  public <A> CompletableFuture fork(Function0<A> value) {
    return CompletableFuture.supplyAsync(value::apply, executor);
  }

  @Override
  public CompletableFuture unit() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public <A> CompletableFuture fromTry(Function0<Try<A>> lazyValue) {
    Try<A> value = lazyValue.apply();
    CompletableFuture future = new CompletableFuture();
    applyTryToFuture(value, future);
    return future;
  }

  @Override
  public <A> Promise<CompletableFuture, A> promise() {
    CompletableFuture future = new CompletableFuture();
    return new Async.Promise<>(future, value -> {
      applyTryToFuture(value, future);
      return BoxedUnit.UNIT;
    });
  }

  @Override
  public <A, B> CompletableFuture flatMap(CompletableFuture m, Function1<A, CompletableFuture> f) {
    return m.thenCompose(x -> f.apply((A) x));
  }

  @Override
  public <A, B> CompletableFuture map(CompletableFuture m, Function1<A, B> f) {
    return m.thenApply(x -> f.apply((A) x));
  }

  @Override
  public <A, U> CompletableFuture recover(CompletableFuture m, PartialFunction<Throwable, U> f) {
    return m.handle((result, exception) -> exception == null ? result : f.apply((Throwable) exception));
  }

  @Override
  public <A extends Object, M extends TraversableOnce<Object>> CompletableFuture sequence(M in, CanBuildFrom<M, A, M> cbf) {

    Builder<A, M> builder = cbf.apply(in);
    CompletableFuture result = new CompletableFuture();
    AtomicInteger completed = new AtomicInteger();
    int expected = in.size() - 1;

    in.foreach(future ->
      ((CompletableFuture) future).whenComplete(
        (value, exception) -> {
          if (exception == null && expected == completed.get()) {
            builder.$plus$eq((A) value);
            result.complete(builder.result());
          } else if (exception == null) {
            builder.$plus$eq((A) value);
            completed.incrementAndGet();
            result.complete(builder.result());
          } else {
            result.completeExceptionally((Throwable) exception);
          }
        }
      )
    );

    return result;
  }

  @Override
  public <A, U> void run(CompletableFuture m, Function1<Try<A>, U> f) {
    m.whenComplete((value, exception) -> {
      if (exception == null) {
        f.apply(Success.apply((A) value));
      } else {
        f.apply(Failure.apply((Throwable) exception));
      }
    });
  }

  private <A> void applyTryToFuture(Try<A> value, CompletableFuture future) {
    if (value instanceof Failure) {
      Option<Throwable> maybeException = Failure.unapply((Failure) value);
      future.completeExceptionally(maybeException.get());
    } else if (value instanceof Success) {
      Option<A> maybeValue = Success.unapply((Success) value);
      future.complete(maybeValue.get());
    }
  }
}
