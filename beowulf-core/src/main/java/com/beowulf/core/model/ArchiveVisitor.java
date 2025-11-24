package com.beowulf.core.model;

import com.beowulf.core.facade.ArchivePersistenceService;
import com.beowulf.core.interfaces.LogVisitor;

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
