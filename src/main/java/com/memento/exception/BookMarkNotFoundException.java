package com.memento.exception;

public class BookMarkNotFoundException extends RuntimeException {

    public BookMarkNotFoundException(String title) {
        super("Bookmark not found: " + title);
    }

}