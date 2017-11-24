import com.tenderowls.korolev.java.Renderer;
import com.tenderowls.korolev.java.Service;
import io.vavr.collection.List;
import korolev.Async;
import korolev.state.StateSerializer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TodoListService extends Service<TodoListState, Object> {

  public TodoListService(StateSerializer<TodoListState> serializer, Async<CompletableFuture> async) {
    super(serializer, async);
  }

  @Override
  public CompletableFuture<TodoListState> getDefaultState(String deviceId) {
    List<TodoListState.Todo> todos = List.of(
      new TodoListState.Todo(UUID.randomUUID(), "Drink vodka", false),
      new TodoListState.Todo(UUID.randomUUID(), "Dance with bear", false)
    );
    return CompletableFuture.completedFuture(new TodoListState(todos));
  }

  @Override
  public Renderer<TodoListState, Object> getRenderer() {
    return new TodoListRenderer(async);
  }
}
