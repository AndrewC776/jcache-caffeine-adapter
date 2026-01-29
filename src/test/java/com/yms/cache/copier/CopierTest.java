package com.yms.cache.copier;

import org.junit.jupiter.api.Test;
import java.io.Serializable;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for Copier implementations.
 * Tests identity (store-by-reference) and serializing (store-by-value) copiers.
 */
class CopierTest {

    // ========== IdentityCopier Tests ==========

    @Test
    void identityCopierShouldReturnSameReference() {
        Copier<String> copier = new IdentityCopier<>();
        String original = "test";

        String copied = copier.copy(original);

        assertSame(original, copied);
    }

    @Test
    void identityCopierShouldHandleNull() {
        Copier<String> copier = new IdentityCopier<>();

        assertNull(copier.copy(null));
    }

    @Test
    void identityCopierShouldWorkWithComplexObjects() {
        Copier<TestValue> copier = new IdentityCopier<>();
        TestValue original = new TestValue("data");

        TestValue copied = copier.copy(original);

        assertSame(original, copied);
    }

    // ========== SerializingCopier Tests ==========

    @Test
    void serializingCopierShouldReturnDifferentReference() {
        Copier<TestValue> copier = new SerializingCopier<>();
        TestValue original = new TestValue("test");

        TestValue copied = copier.copy(original);

        assertNotSame(original, copied);
        assertEquals(original.getData(), copied.getData());
    }

    @Test
    void serializingCopierShouldHandleNull() {
        Copier<TestValue> copier = new SerializingCopier<>();

        assertNull(copier.copy(null));
    }

    @Test
    void serializingCopierShouldPreserveData() {
        Copier<TestValue> copier = new SerializingCopier<>();
        TestValue original = new TestValue("important data");

        TestValue copied = copier.copy(original);

        assertEquals("important data", copied.getData());
    }

    @Test
    void serializingCopierShouldThrowForNonSerializable() {
        Copier<NonSerializableValue> copier = new SerializingCopier<>();
        NonSerializableValue value = new NonSerializableValue("test");

        assertThrows(IllegalArgumentException.class, () -> copier.copy(value));
    }

    @Test
    void serializingCopierShouldCopyStrings() {
        Copier<String> copier = new SerializingCopier<>();
        String original = "test string";

        String copied = copier.copy(original);

        // Strings are interned, but serialization creates new instance
        assertEquals(original, copied);
    }

    @Test
    void serializingCopierShouldCopyNestedObjects() {
        Copier<NestedValue> copier = new SerializingCopier<>();
        NestedValue original = new NestedValue("outer", new TestValue("inner"));

        NestedValue copied = copier.copy(original);

        assertNotSame(original, copied);
        assertNotSame(original.getNested(), copied.getNested());
        assertEquals(original.getName(), copied.getName());
        assertEquals(original.getNested().getData(), copied.getNested().getData());
    }

    // ========== Test Helper Classes ==========

    static class TestValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String data;

        TestValue(String data) {
            this.data = data;
        }

        String getData() {
            return data;
        }
    }

    static class NonSerializableValue {
        private final String data;

        NonSerializableValue(String data) {
            this.data = data;
        }
    }

    static class NestedValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final TestValue nested;

        NestedValue(String name, TestValue nested) {
            this.name = name;
            this.nested = nested;
        }

        String getName() {
            return name;
        }

        TestValue getNested() {
            return nested;
        }
    }
}
