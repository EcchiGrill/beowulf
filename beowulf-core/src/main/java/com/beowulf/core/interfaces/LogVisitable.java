package com.beowulf.core.interfaces;

/**
 * Interface for objects that can be visited by a log visitor.
 */
public interface LogVisitable {
    void accept(LogVisitor visitor);
}