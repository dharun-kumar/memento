package com.memento.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Used for PUT /api/bookmarks/{title} — title comes from path, only description + tag in body
public record UpdateBookMarkRequest(
        @NotBlank(message = "Description must not be blank")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Size(max = 100, message = "Tag must not exceed 100 characters")
        String tag
) { }
