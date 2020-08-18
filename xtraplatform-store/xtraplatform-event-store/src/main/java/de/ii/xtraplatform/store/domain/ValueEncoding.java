package de.ii.xtraplatform.store.domain;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface ValueEncoding<T> {

    ObjectMapper getMapper(FORMAT format);

    enum FORMAT {
        UNKNOWN, JSON, YML, YAML/*, ION*/;

        public static FORMAT fromString(String format) {
            if (Objects.isNull(format)) {
                return UNKNOWN;
            }

            for (FORMAT f: values()) {
                if (Objects.equals(f.name(), format.toUpperCase())) {
                    return f;
                }
            }

            throw new IllegalArgumentException();
        }
    }

    FORMAT getDefaultFormat();

    byte[] serialize(T data);

    byte[] serialize(Map<String, Object> data);

    T deserialize(Identifier identifier, byte[] payload, FORMAT format);

    byte[] nestPayload(byte[] payload, String format, List<String> nestingPath, Optional<KeyPathAlias> keyPathAlias) throws IOException;

}