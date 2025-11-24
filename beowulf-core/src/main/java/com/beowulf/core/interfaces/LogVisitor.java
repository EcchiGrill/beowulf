package com.beowulf.core.interfaces;

import com.beowulf.core.model.ArchiveOperation;

/**
 * Interface for objects that can visit a loggable object.
 */
public interface LogVisitor {
    void visit(ArchiveOperation context);
}
