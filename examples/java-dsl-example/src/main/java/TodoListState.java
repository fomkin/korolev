import io.vavr.collection.List;
import lombok.Value;
import lombok.experimental.Wither;

import java.util.UUID;

@Value class TodoListState {

  @Wither List<Todo> todos;

  @Value
  static class Todo {
    @Wither UUID id;
    @Wither String text;
    @Wither boolean done;
  }
}
