package com.memento.repo;

import com.memento.model.BookMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookMarkRepo extends JpaRepository<BookMark, String> {

    // All queries scoped to userId — users never see each other's bookmarks
    List<BookMark> findByUserId(Long userId);

    // Spring Data derives: SELECT * FROM bookmarks WHERE title = ? AND user_id = ?
    Optional<BookMark> findByTitleAndUserId(String title, Long userId);

    // Spring Data derives: SELECT * FROM bookmarks WHERE user_id = ? AND tag = ?
    List<BookMark> findByUserIdAndTag(Long userId, String tag);

    // Prevent duplicate titles per user (composite PK also enforces this at DB level)
    boolean existsByTitleAndUserId(String title, Long userId);

    // Scoped delete — ensures user can only delete their own bookmarks
    void deleteByTitleAndUserId(String title, Long userId);

}
