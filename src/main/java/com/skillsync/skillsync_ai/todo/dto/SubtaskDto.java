package com.skillsync.skillsync_ai.todo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtaskDto implements Serializable {
    private String id;
    private String title;
    private boolean completed;
}
