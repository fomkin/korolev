import com.tenderowls.korolev.java.BrowserAccess;
import com.tenderowls.korolev.java.Renderer;
import com.tenderowls.korolev.java.effects.Effect;
import com.tenderowls.korolev.java.effects.EffectsProvider;
import com.tenderowls.korolev.java.effects.ElementBinding;
import io.vavr.collection.List;
import korolev.Async;
import levsha.Document.Node;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TodoListRenderer extends Renderer<TodoListState, Object> {

  private final ElementBinding<TodoListState, Object> todoInputBinding =
    createElementBinding("new-todo-input");

  public TodoListRenderer(Async<CompletableFuture> async) {
    super(async);
  }

  @Override
  public Node<Effect<TodoListState, Object>> render(
      EffectsProvider<TodoListState, Object> effects,
      TodoListState stateToRender) {

    return html.body(
      html.h1(text("The TODO list")),
      html.ul(
        renderValues(
          stateToRender.getTodos(),
          todo -> html.li(
            text(todo.getText() + (todo.isDone() ? " [DONE]" : "")),
            effects.handleEvent("click", browserAccess -> handleTodoClick(todo, browserAccess))
          )
        )
      ),
      html.form(
        html.input(
          html.type("text"),
          html.placeholder("New todo text"),
          effects.bind(todoInputBinding)
        ),
        html.button(text("Add new todo")),
        effects.handleEvent("submit", this::handleSubmit)
      )
    );
  }

  private CompletableFuture<Void> handleTodoClick(
      TodoListState.Todo todo,
      BrowserAccess<TodoListState, Object> browserAccess) {
    return browserAccess.makeTransition(state -> {
      TodoListState.Todo updatedTodo = todo.withDone(!todo.isDone());
      List<TodoListState.Todo> updatedList = state.getTodos().replace(todo, updatedTodo);
      return state.withTodos(updatedList);
    });
  }

  private CompletableFuture<Void> handleSubmit(BrowserAccess<TodoListState, Object> browserAccess) {
    return browserAccess.valueOf(todoInputBinding).thenCompose(
      value -> browserAccess.makeTransition(state -> {
        TodoListState.Todo newTodo = new TodoListState.Todo(UUID.randomUUID(), value, false);
        return state.withTodos(state.getTodos().append(newTodo));
      })
    );
  }
}
