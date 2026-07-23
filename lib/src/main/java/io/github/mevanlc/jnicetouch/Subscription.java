package io.github.mevanlc.jnicetouch;

/** A listener registration that can be removed idempotently. */
public interface Subscription extends AutoCloseable {
    /**
     * Returns whether the listener is still registered.
     *
     * @return whether the subscription is active
     */
    boolean isActive();

    @Override
    void close();
}
