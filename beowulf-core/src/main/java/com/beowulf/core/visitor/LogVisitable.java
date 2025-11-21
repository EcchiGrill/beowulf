package com.beowulf.core.visitor;

public interface LogVisitable {
    void accept(LogVisitor visitor);
}