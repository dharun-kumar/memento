package com.memento.controller;

import com.memento.dto.request.UpdateBookMarkRequest;
import com.memento.model.BookMark;
import com.memento.service.BookMarkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
public class BookMarkController {

    // Constructor injection (preferred over @Autowired field injection)
    private final BookMarkService service;

    public BookMarkController(BookMarkService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<BookMark>> getAllBookMarks() {
        return ResponseEntity.ok(service.getAllBookMarks());
    }

    // Filter by tag: GET /api/bookmarks?tag=dev
    @GetMapping(params = "tag")
    public ResponseEntity<List<BookMark>> getBookMarksByTag(@RequestParam String tag) {
        return ResponseEntity.ok(service.getBookMarksByTag(tag));
    }

    @GetMapping("/{title}")
    public ResponseEntity<BookMark> getBookMark(@PathVariable String title) {
        return service.getBookMark(title)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // @Valid triggers Bean Validation on the @RequestBody before the method runs.
    // If validation fails, MethodArgumentNotValidException is thrown and
    // our @ControllerAdvice returns a structured 400 response.
    @PostMapping
    public ResponseEntity<BookMark> createBookMark(@Valid @RequestBody BookMark bookMark) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBookMark(bookMark));
    }

    // title from path, only description + tag in body via UpdateBookMarkRequest
    @PutMapping("/{title}")
    public ResponseEntity<BookMark> updateBookMark(@PathVariable String title,
                                                    @Valid @RequestBody UpdateBookMarkRequest request) {
        return ResponseEntity.ok(service.updateBookMark(title, request));
    }

    @DeleteMapping("/{title}")
    public ResponseEntity<Void> deleteBookMark(@PathVariable String title) {
        service.deleteBookMark(title);
        return ResponseEntity.noContent().build();
    }

}
