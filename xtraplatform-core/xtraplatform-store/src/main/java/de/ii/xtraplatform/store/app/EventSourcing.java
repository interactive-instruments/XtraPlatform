/*
 * Copyright 2019-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.store.app;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.ii.xtraplatform.runtime.domain.LogContext;
import de.ii.xtraplatform.store.domain.EntityEvent;
import de.ii.xtraplatform.store.domain.EventFilter;
import de.ii.xtraplatform.store.domain.EventStore;
import de.ii.xtraplatform.store.domain.EventStoreSubscriber;
import de.ii.xtraplatform.store.domain.Identifier;
import de.ii.xtraplatform.store.domain.ImmutableMutationEvent;
import de.ii.xtraplatform.store.domain.MutationEvent;
import de.ii.xtraplatform.store.domain.ReloadEvent;
import de.ii.xtraplatform.store.domain.ReplayEvent;
import de.ii.xtraplatform.store.domain.StateChangeEvent;
import de.ii.xtraplatform.store.domain.ValueCache;
import de.ii.xtraplatform.store.domain.ValueEncoding;
import de.ii.xtraplatform.streams.domain.Event;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: should this really be a facade for EventStore? or can we make it plain ValueCache?
public class EventSourcing<T> implements EventStoreSubscriber, ValueCache<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventSourcing.class);

  private final Map<Identifier, T> cache;
  private final Map<Identifier, CompletableFuture<T>> queue;
  private final EventStore eventStore;
  private final List<String> eventTypes;
  private final ValueEncoding<T> valueEncoding;
  private final Supplier<CompletableFuture<Void>> onStart;
  private final Optional<Function<ReplayEvent, List<ReplayEvent>>> replayEventProcessor;
  private final Optional<Function<MutationEvent, List<MutationEvent>>> mutationEventProcessor;
  private final Optional<BiFunction<Identifier, T, CompletableFuture<Void>>> updateHook;
  private final Optional<BiConsumer<Identifier, T>> valueValidator;
  private final Set<String> started;
  private final ExecutorService executorService;

  public EventSourcing(
      EventStore eventStore,
      List<String> eventTypes,
      ValueEncoding<T> valueEncoding,
      Supplier<CompletableFuture<Void>> onStart,
      Optional<Function<ReplayEvent, List<ReplayEvent>>> replayEventProcessor,
      Optional<Function<MutationEvent, List<MutationEvent>>> mutationEventProcessor,
      Optional<BiFunction<Identifier, T, CompletableFuture<Void>>> updateHook) {
    this(
        eventStore,
        eventTypes,
        valueEncoding,
        onStart,
        replayEventProcessor,
        mutationEventProcessor,
        updateHook,
        Optional.empty());
  }

  public EventSourcing(
      EventStore eventStore,
      List<String> eventTypes,
      ValueEncoding<T> valueEncoding,
      Supplier<CompletableFuture<Void>> onStart,
      Optional<Function<ReplayEvent, List<ReplayEvent>>> replayEventProcessor,
      Optional<Function<MutationEvent, List<MutationEvent>>> mutationEventProcessor,
      Optional<BiFunction<Identifier, T, CompletableFuture<Void>>> updateHook,
      Optional<BiConsumer<Identifier, T>> valueValidator) {
    this.eventStore = eventStore;
    this.eventTypes = eventTypes;
    this.replayEventProcessor = replayEventProcessor;
    this.mutationEventProcessor = mutationEventProcessor;
    this.updateHook = updateHook;
    this.cache = new ConcurrentSkipListMap<>();
    this.queue = new ConcurrentHashMap<>();
    this.valueEncoding = valueEncoding;
    this.onStart = onStart;
    this.valueValidator = valueValidator;
    this.started = new HashSet<>();
    this.executorService =
        MoreExecutors.getExitingExecutorService(
            (ThreadPoolExecutor)
                Executors.newFixedThreadPool(
                    2, new ThreadFactoryBuilder().setNameFormat("stream.events-%d").build()));

    eventStore.subscribe(this);
  }

  @Override
  public List<String> getEventTypes() {
    return eventTypes;
  }

  @Override
  public void onEmit(Event event) {
    if (event instanceof EntityEvent) {
      EntityEvent entityEvent = (EntityEvent) event;
      try {
        if (replayEventProcessor.isPresent() && event instanceof ReplayEvent) {
          for (ReplayEvent replayEvent : replayEventProcessor.get().apply((ReplayEvent) event)) {
            onEmit(replayEvent);
          }
        } else if (mutationEventProcessor.isPresent() && event instanceof MutationEvent) {
          CompletableFuture<T> completableFuture = null;
          // TODO
          if (queue.containsKey(entityEvent.identifier())
              && entityEvent.type().equals("defaults")
              && entityEvent.identifier().id().equals("services.ogc_api")) {
            completableFuture = queue.get(entityEvent.identifier());
            queue.remove(entityEvent.identifier());
          }
          for (MutationEvent mutationEvent :
              mutationEventProcessor.get().apply((MutationEvent) event)) {
            if (Objects.nonNull(completableFuture)) {
              queue.put(mutationEvent.identifier(), completableFuture);
            }
            onEmit(mutationEvent);
          }
        } else {
          onEmit(entityEvent);
        }
      } catch (Throwable e) {
        LogContext.error(LOGGER, e, "Cannot load '{}'", entityEvent.asPath());
      }

    } else if (event instanceof StateChangeEvent) {
      switch (((StateChangeEvent) event).state()) {
        case REPLAYING:
          LOGGER.debug("Replaying events for {}", ((StateChangeEvent) event).type());
          break;
        case LISTENING:
          started.add(((StateChangeEvent) event).type());

          if (started.containsAll(getEventTypes())) {
            onStart.get().thenRun(() -> LOGGER.debug("Listening for events for {}", started));
          }
          break;
      }
    } else if (event instanceof ReloadEvent && updateHook.isPresent()) {
      List<Identifier> identifiers = getIdentifiers(((ReloadEvent) event).filter());

      identifiers.stream()
          // TODO: set priority per entity type (for now alphabetic works:
          //  codelists < providers < services)
          .sorted(Comparator.comparing(identifier -> identifier.path().get(0)))
          .reduce(
              CompletableFuture.completedFuture((Void) null),
              (completableFuture, identifier) ->
                  completableFuture.thenCompose(
                      ignore2 -> updateHook.get().apply(identifier, getFromCache(identifier))),
              (first, second) -> first.thenCompose(ignore2 -> second))
          .join();
    }
  }

  @Override
  public boolean isInCache(Identifier identifier) {
    return cache.containsKey(identifier);
  }

  @Override
  public T getFromCache(Identifier identifier) {
    return cache.get(identifier);
  }

  public List<Identifier> getIdentifiers(String... path) {
    return cache.keySet().stream()
        .filter(
            identifier ->
                path.length == 0 || Objects.equals(ImmutableList.copyOf(path), identifier.path()))
        .collect(Collectors.toList());
  }

  public CompletableFuture<T> pushMutationEvent(Identifier identifier, T data) {
    final byte[] payload = valueEncoding.serialize(data);

    return pushMutationEventRaw(identifier, payload, Objects.isNull(data));
  }

  public CompletableFuture<T> pushPartialMutationEvent(
      Identifier identifier, Map<String, Object> data) {
    final byte[] payload = valueEncoding.serialize(data);

    return pushMutationEventRaw(identifier, payload, Objects.isNull(data));
  }

  public CompletableFuture<T> pushMutationEventRaw(Identifier identifier, byte[] payload) {
    return pushMutationEventRaw(identifier, payload, false);
  }

  // TODO: which eventType should we push?
  private CompletableFuture<T> pushMutationEventRaw(
      Identifier identifier, byte[] payload, boolean isDelete) {
    final CompletableFuture<T> completableFuture = new CompletableFuture<>();

    try {
      // TODO: if already in queue, pipeline to existing future
      final EntityEvent entityEvent =
          ImmutableMutationEvent.builder()
              .type(eventTypes.get(0))
              .identifier(identifier)
              .payload(payload)
              .deleted(isDelete ? true : null)
              .format(valueEncoding.getDefaultFormat().toString())
              .build();

      queue.put(identifier, completableFuture);

      // TODO: pass snapshot to push, event store can decide what to do with it
      // who decides if snapshotting is enabled?
      eventStore.push(entityEvent);

    } catch (Throwable e) {
      completableFuture.completeExceptionally(e);
      return completableFuture;
    }

    return completableFuture;
  }

  private void onEmit(EntityEvent event) throws Throwable {
    Identifier key = event.identifier();
    ValueEncoding.FORMAT payloadFormat = ValueEncoding.FORMAT.fromString(event.format());

    if (payloadFormat == ValueEncoding.FORMAT.UNKNOWN) {
      if (queue.containsKey(key)) {
        queue.remove(key).complete(null);
      }
      return;
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Adding event: {} {} {}", event.type(), event.identifier(), event.format());
    }

    T value;
    Throwable error = null;

    try {
      value =
          valueEncoding.deserialize(
              event.identifier(), event.payload(), payloadFormat, event instanceof MutationEvent);

    } catch (Throwable e) {
      error = e;
      value = cache.getOrDefault(key, null);
    }

    if (Objects.isNull(error) && !Objects.isNull(value) && valueValidator.isPresent()) {
      try {
        valueValidator.get().accept(key, value);
      } catch (Throwable e) {
        error = e.getCause();
        value = cache.getOrDefault(key, null);
      }
    }

    if (Objects.isNull(value)) {
      cache.remove(key);
    } else {
      cache.put(key, value);
    }

    if (queue.containsKey(key)) {
      T finalValue = value;
      // need to use async, otherwise may produce deadlock
      queue.remove(key).completeAsync(() -> finalValue, executorService);
    }

    if (!Objects.isNull(error)) {
      throw error;
    }
  }

  private List<Identifier> getIdentifiers(EventFilter filter) {

    return getIdentifiers().stream()
        .filter(
            identifier -> {
              if (filter.getEntityTypes().contains("*")
                  || (!identifier.path().isEmpty()
                      && filter.getEntityTypes().contains(identifier.path().get(0)))) {
                return filter.getIds().contains("*") || filter.getIds().contains(identifier.id());
              }

              return false;
            })
        .collect(Collectors.toList());
  }
}
