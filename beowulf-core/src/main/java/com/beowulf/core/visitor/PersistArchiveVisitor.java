package com.beowulf.core.visitor;

public class PersistArchiveVisitor implements LogVisitor {

    private final ArchivePersistenceService service;

    public PersistArchiveVisitor(ArchivePersistenceService service) {
        this.service = service;
    }

    @Override
    public void visit(ArchiveOperationContext context) {
        service.persistOperation(context);
    }
}
