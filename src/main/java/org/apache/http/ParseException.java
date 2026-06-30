package org.apache.http;

/**
 * Compatibility shim for legacy 1.12 mods that still link against HttpCore 4.x.
 */
public class ParseException extends RuntimeException {
    private static final long serialVersionUID = -7288819855864183578L;

    public ParseException() {
        super();
    }

    public ParseException(String message) {
        super(message);
    }
}
