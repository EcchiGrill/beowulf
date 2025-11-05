package com.beowulf.core;

import com.beowulf.core.archiver.Archiver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchiverTest {
    @Test
    void archiverHasGreeting() {
        Archiver classUnderTest = new Archiver();
        assertEquals("Hello World!", classUnderTest.getGreeting(), "Archiver should return 'Hello World!'");
    }
}
