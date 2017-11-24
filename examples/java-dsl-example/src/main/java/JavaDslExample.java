import com.tenderowls.korolev.java.CompletableFutureAsync;
import korolev.blazeServer.BlazeServerConfig;
import korolev.blazeServer.BlazeServiceBuilder;
import korolev.execution.package$;
import korolev.state.StateDeserializer;
import korolev.state.StateSerializer;
import korolev.state.javaSerialization;
import scala.concurrent.ExecutionContextExecutorService;
import slogging.LoggerConfig;
import slogging.SLF4JLoggerFactory;

import java.util.concurrent.CompletableFuture;

public class JavaDslExample {
  public static void main(String[] args) {
    LoggerConfig.factory_$eq(SLF4JLoggerFactory.apply());
    ExecutionContextExecutorService executor = package$.MODULE$.defaultExecutor();
    CompletableFutureAsync async = new CompletableFutureAsync(executor);
    StateSerializer<TodoListState> serializer = javaSerialization.serializer();
    StateDeserializer<TodoListState> deserializer = javaSerialization.deserializer();
    TodoListService korolevService = new TodoListService(serializer, async);
    BlazeServiceBuilder<CompletableFuture, TodoListState, Object> serviceBuilder = korolev.blazeServer.package$.MODULE$.blazeService(async, serializer, deserializer);
    korolev.blazeServer.package$.MODULE$.runServer(
      serviceBuilder.from(korolevService.asScala()),
      BlazeServerConfig.empty(executor)
    );
  }
}
