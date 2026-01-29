package com.yms.cache.copier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Serializing copier that creates deep copies via Java serialization.
 *
 * <p>Used when {@code storeByValue=true} (store-by-value mode).
 * This ensures cached values are isolated from external mutations,
 * providing true snapshot semantics per JCache specification.
 *
 * <p>Values must implement {@link Serializable}. Non-serializable values
 * will cause an {@link IllegalArgumentException} to be thrown.
 *
 * @param <T> the type of value to copy
 */
public final class SerializingCopier<T> implements Copier<T> {

    @Override
    @SuppressWarnings("unchecked")
    public T copy(T value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException(
                "Value must be Serializable for store-by-value mode: " + value.getClass().getName());
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            oos.close();

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Failed to copy value via serialization", e);
        }
    }
}
