package com.memento.service;

import com.memento.config.AppUserDetails;
import com.memento.dto.request.UpdateBookMarkRequest;
import com.memento.exception.BookMarkExistException;
import com.memento.exception.BookMarkNotFoundException;
import com.memento.model.BookMark;
import com.memento.repo.BookMarkRepo;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BookMarkService {

    private final BookMarkRepo repository;

    public BookMarkService(BookMarkRepo repository) {
        this.repository = repository;
    }

    // Reads the authenticated user's ID from the SecurityContext.
    // JwtAuthFilter (for agents) and Spring's session filter (for browser users)
    // both populate the SecurityContext before any service method is called.
    //
    // Must be public — Spring's SpEL engine calls it via reflection in @Cacheable
    // key expressions like #root.target.currentUserId(). Private methods are not visible.
    public Long currentUserId() {
        AppUserDetails userDetails = (AppUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getId();
    }

    // @Cacheable checks the cache first. If a cached value exists for this key,
    // the method body is skipped and the cached result is returned immediately.
    // Cache key includes userId so each user has an isolated cache slot.
    @Cacheable(value = "bookmarks", key = "'all:' + #root.target.currentUserId()")
    public List<BookMark> getAllBookMarks() {
        return repository.findByUserId(currentUserId());
    }

    @Cacheable(value = "bookmarks", key = "#title + ':' + #root.target.currentUserId()")
    public Optional<BookMark> getBookMark(String title) {
        return repository.findByTitleAndUserId(title, currentUserId());
    }

    @Cacheable(value = "bookmarks", key = "'tag:' + #tag + ':' + #root.target.currentUserId()")
    public List<BookMark> getBookMarksByTag(String tag) {
        return repository.findByUserIdAndTag(currentUserId(), tag);
    }

    // @Transactional: if anything inside fails, the entire DB operation rolls back.
    // @CacheEvict(allEntries = true): clears the whole "bookmarks" cache on any write.
    // Simpler than tracking individual keys — acceptable for a small user base.
    @Transactional
    @CacheEvict(value = "bookmarks", allEntries = true)
    public BookMark createBookMark(BookMark bookMark) {
        Long userId = currentUserId();
        if (repository.existsByTitleAndUserId(bookMark.getTitle(), userId)) {
            throw new BookMarkExistException(bookMark.getTitle());
        }
        // userId always comes from the JWT — the client never sends it
        bookMark.setUserId(userId);
        return repository.save(bookMark);
    }

    @Transactional
    @CacheEvict(value = "bookmarks", allEntries = true)
    public BookMark updateBookMark(String title, UpdateBookMarkRequest request) {
        Long userId = currentUserId();
        BookMark bookmark = repository.findByTitleAndUserId(title, userId)
                .orElseThrow(() -> new BookMarkNotFoundException(title));
        bookmark.setDescription(request.description());
        bookmark.setTag(request.tag());
        return repository.save(bookmark);
    }

    @Transactional
    @CacheEvict(value = "bookmarks", allEntries = true)
    public void deleteBookMark(String title) {
        Long userId = currentUserId();
        if (!repository.existsByTitleAndUserId(title, userId)) {
            throw new BookMarkNotFoundException(title);
        }
        repository.deleteByTitleAndUserId(title, userId);
    }

}
