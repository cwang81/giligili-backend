package com.laioffer.giligili.db;

public class MySQLException extends RuntimeException {
    public MySQLException(String errorMessage) {
        super(errorMessage);
    }
}
