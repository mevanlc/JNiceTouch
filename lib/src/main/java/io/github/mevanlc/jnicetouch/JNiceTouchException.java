package io.github.mevanlc.jnicetouch;

/** Indicates that the native JNiceTouch provider could not be used. */
public final class JNiceTouchException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message diagnostic message
     */
    public JNiceTouchException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a diagnostic message and cause.
     *
     * @param message diagnostic message
     * @param cause underlying failure
     */
    public JNiceTouchException(String message, Throwable cause) {
        super(message, cause);
    }
}
