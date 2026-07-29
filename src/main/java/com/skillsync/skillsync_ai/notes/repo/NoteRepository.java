package com.skillsync.skillsync_ai.notes.repo;

import com.skillsync.skillsync_ai.notes.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {

    // Fetch all notes, pinned notes at the top, then sorted by most recently updated
    Page<Note> findAllByOwnerUserIdOrderByPinnedDescUpdatedAtDesc(UUID ownerUserId, Pageable pageable);

    // Fetch notes specific to a project
    Page<Note> findAllByOwnerUserIdAndProjectIdOrderByPinnedDescUpdatedAtDesc(UUID ownerUserId, UUID projectId, Pageable pageable);

    // Secure single fetch
    Optional<Note> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}