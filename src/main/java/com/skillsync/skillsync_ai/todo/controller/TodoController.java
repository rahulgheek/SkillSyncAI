package com.skillsync.skillsync_ai.todo.controller;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.common.response.ApiResponse;
import com.skillsync.skillsync_ai.todo.dto.TodoRequest;
import com.skillsync.skillsync_ai.todo.dto.TodoResponse;
import com.skillsync.skillsync_ai.todo.entity.enums.TodoStatus;
import com.skillsync.skillsync_ai.todo.services.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TodoResponse>>> getMyTodos(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) TodoStatus status,
            @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully",
                todoService.getUserTodos(currentUser.getId(), sessionId, status, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TodoResponse>> createTodo(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TodoRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created successfully",
                        todoService.createTodo(currentUser.getId(), request)));
    }

    @PutMapping("/{todoId}")
    public ResponseEntity<ApiResponse<TodoResponse>> updateTodo(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID todoId,
            @Valid @RequestBody TodoRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Task updated successfully",
                todoService.updateTodo(currentUser.getId(), todoId, request)));
    }

    @PatchMapping("/{todoId}/status")
    public ResponseEntity<ApiResponse<TodoResponse>> updateTodoStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID todoId,
            @RequestParam TodoStatus status) {

        return ResponseEntity.ok(ApiResponse.success("Task status updated",
                todoService.updateStatus(currentUser.getId(), todoId, status)));
    }

    @PatchMapping("/{todoId}/subtasks/{subtaskId}/toggle")
    public ResponseEntity<ApiResponse<TodoResponse>> toggleSubtask(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID todoId,
            @PathVariable String subtaskId) {

        return ResponseEntity.ok(ApiResponse.success("Subtask toggled",
                todoService.toggleSubtask(currentUser.getId(), todoId, subtaskId)));
    }

    @DeleteMapping("/{todoId}")
    public ResponseEntity<ApiResponse<Void>> deleteTodo(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID todoId) {

        todoService.deleteTodo(currentUser.getId(), todoId);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully", null));
    }
}