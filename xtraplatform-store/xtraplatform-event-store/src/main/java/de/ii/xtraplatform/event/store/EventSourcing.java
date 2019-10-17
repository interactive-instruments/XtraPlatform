package de.ii.xtraplatform.event.store;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EventSourcing<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventSourcing.class);

    private final Map<Identifier, T> cache;
    private final Map<Identifier, CompletableFuture<T>> queue;
    private final EventStore eventStore;
    private final String eventType;
    private final Function<T, byte[]> serializer;
    private final BiFunction<Identifier, byte[], T> deserializer;

    public EventSourcing(EventStore eventStore, String eventType, Function<T, byte[]> serializer,
                         BiFunction<Identifier, byte[], T> deserializer) {
        this.eventStore = eventStore;
        this.eventType = eventType;
        this.cache = new ConcurrentHashMap<>();
        this.queue = new ConcurrentHashMap<>();
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    public boolean isInCache(Identifier identifier) {
        return cache.containsKey(identifier);
    }

    public T getFromCache(Identifier identifier) {
        return cache.get(identifier);
    }

    public List<Identifier> getIdentifiers(String... path) {
        return cache.keySet()
                    .stream()
                    .filter(identifier -> path.length == 0 || Objects.equals(ImmutableList.copyOf(path), identifier.path()))
                    .collect(Collectors.toList());
    }

    public CompletableFuture<T> pushMutationEvent(Identifier identifier, T data) {
        final byte[] payload = serializer.apply(data);

        return pushMutationEventRaw(identifier, payload, Objects.isNull(data));
    }

    public CompletableFuture<T> pushMutationEventRaw(Identifier identifier, byte[] payload) {
        return pushMutationEventRaw(identifier, payload, false);
    }

    private CompletableFuture<T> pushMutationEventRaw(Identifier identifier, byte[] payload, boolean isDelete) {
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();

        try {
            //TODO: if already in queue, pipeline to existing future
            final MutationEvent mutationEvent = ImmutableMutationEvent.builder()
                                                                      .type(eventType)
                                                                      .identifier(identifier)
                                                                      .payload(payload)
                                                                      .deleted(isDelete ? true : null)
                                                                      .build();

            queue.put(identifier, completableFuture);

            //TODO: pass snapshot to push, event store can decide what to do with it
            // who decides if snapshotting is enabled?
            eventStore.push(mutationEvent);

        } catch (Throwable e) {
            completableFuture.completeExceptionally(e);
            return completableFuture;
        }

        return completableFuture;
    }

    public void onEmit(MutationEvent event) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Adding event: {} {}", event.type(), event.identifier());
        }

        T value = deserializer.apply(event.identifier(), event.payload());

        if (Objects.isNull(value)) {
            cache.remove(event.identifier());
        } else {
            cache.put(event.identifier(), value);
        }

        if (queue.containsKey(event.identifier())) {
            queue.remove(event.identifier())
                 .complete(value);
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("GOT {}", value);
            LOGGER.trace("CACHE {}", cache);
        }
    }

}