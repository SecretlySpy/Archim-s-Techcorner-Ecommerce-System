package com.archimstechcorner.marketplace.shared.support;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Maps a {@link UUID} to a compact 16-byte {@code BINARY(16)} column and back.
 *
 * <p>Public identifiers are stored as bytes (not 36-char text) so joins and indexes stay small,
 * matching the {@code public_id BINARY(16)} columns in the identity migrations.
 */
@Converter(autoApply = false)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        // Encode the two 64-bit halves in big-endian order for stable, sortable bytes.
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    @Override
    public UUID convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
