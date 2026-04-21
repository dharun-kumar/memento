package com.memento.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "bookmarks")
public class BookMark {

    // title is the natural key — users refer to bookmarks by name, not a generated id
    @Id
    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String title;

    @NotBlank(message = "Description must not be blank")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Column(nullable = false, length = 2000)
    private String description;

    @Size(max = 100, message = "Tag must not exceed 100 characters")
    private String tag;

    @JsonIgnore // never expose userId in API responses — internal field only
    @Column(name = "user_id", nullable = false)
    private Long userId; // set by the service from JWT — client never sends this

    public BookMark() { }

    public BookMark(String title, String description, String tag, Long userId) {
        this.title = title;
        this.description = description;
        this.tag = tag;
        this.userId = userId;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTag() { return tag; }
    public Long getUserId() { return userId; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setTag(String tag) { this.tag = tag; }
    public void setUserId(Long userId) { this.userId = userId; }

}
