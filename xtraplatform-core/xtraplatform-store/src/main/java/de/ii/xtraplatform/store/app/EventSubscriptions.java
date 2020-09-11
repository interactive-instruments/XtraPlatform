/*
 * Copyright 2019-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.store.app;

import akka.stream.ActorMaterializer;
import de.ii.xtraplatform.store.domain.EventStoreSubscriber;
import de.ii.xtraplatform.store.domain.ImmutableStateChangeEvent;
import de.ii.xtraplatform.store.domain.MutationEvent;
import de.ii.xtraplatform.store.domain.StateChangeEvent;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO
public class EventSubscriptions {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventSubscriptions.class);

  private final Map<String, EventStream> eventStreams;
  private final ActorMaterializer materializer;
  private final ScheduledExecutorService executorService;
  private boolean isStarted;

  protected EventSubscriptions(ActorMaterializer materializer) {
    this.eventStreams = new ConcurrentHashMap<>();
    this.materializer = materializer;
    this.executorService = new ScheduledThreadPoolExecutor(1);
  }

  public void addSubscriber(EventStoreSubscriber subscriber) {
    // TODO: we need the 10 second delay to wait for all JacksonSubTypeIds, find a better solution
    executorService.schedule(
        () -> {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "New event store subscriber: {} {}", subscriber.getEventTypes(), subscriber);
          }

          for (String eventType : subscriber.getEventTypes()) {
            EventStream eventStream = getEventStream(eventType);
            CompletableFuture<Void> cmp = new CompletableFuture<>();
            eventStream.foreach(
                event -> {
                  if (event instanceof StateChangeEvent
                      && ((StateChangeEvent) event).state() == StateChangeEvent.STATE.LISTENING) {
                    // LOGGER.debug("{} STARTED", eventType);
                    cmp.complete(null);
                  }
                  subscriber.onEmit(event);
                });
            cmp.join();
            // LOGGER.debug("NEXT");
          }
        },
        10,
        TimeUnit.SECONDS);
  }

  public void emitEvent(MutationEvent event) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Emitting event: {} {}", event.type(), event.identifier());
    }
    final EventStream eventStream = getEventStream(event.type());

    eventStream.queue(event);
  }

  public void startListening() {
    eventStreams
        .values()
        .forEach(
            eventStream ->
                emitStateChange(
                    eventStream, StateChangeEvent.STATE.LISTENING, eventStream.getEventType()));
    this.isStarted = true;
  }

  private synchronized EventStream getEventStream(String eventType) {
    Objects.requireNonNull(eventType, "eventType may not be null");
    return eventStreams.computeIfAbsent(eventType, prefix -> createEventStream(eventType));
  }

  private EventStream createEventStream(String eventType) {
    EventStream eventStream = new EventStream(materializer, eventType);

    emitStateChange(eventStream, StateChangeEvent.STATE.REPLAYING, eventType);

    // should only happen if there is no replay, so order would be correct
    if (isStarted) {
      emitStateChange(eventStream, StateChangeEvent.STATE.LISTENING, eventType);
    }

    return eventStream;
  }

  private void emitStateChange(EventStream eventStream, StateChangeEvent.STATE state, String type) {
    eventStream.queue(ImmutableStateChangeEvent.builder().state(state).type(type).build());
  }
}
