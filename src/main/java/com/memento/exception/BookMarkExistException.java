package com.memento.exception;

public class BookMarkExistException extends RuntimeException {

    public BookMarkExistException(String title) {
        super("Bookmark already exist: " + title);
    }

}