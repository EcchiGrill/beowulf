package com.beowulf.core.visitor;

import com.beowulf.core.interfaces.LogVisitor;
import com.beowulf.core.service.ArchivePersistenceService;

public class ArchiveVisitor implements LogVisitor {

    private final ArchivePersistenceService service;

    public ArchiveVisitor(ArchivePersistenceService service) {
        this.service = service;
    }

    @Override
    public void visit(ArchiveOperation context) {
        service.persistOperation(context);
    }
}
