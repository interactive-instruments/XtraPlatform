/*
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.store.domain;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMergeableKeyValueStore<T> extends AbstractKeyValueStore<T>
    implements MergeableKeyValueStore<T> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractMergeableKeyValueStore.class);

  protected abstract ValueEncoding<T> getValueEncoding();

  // TODO: an in-progress event (e.g. drop) might invalidate this one, do we need distributed
  // locks???
  private boolean isUpdateValid(Identifier identifier, byte[] payload) {
    return getEventSourcing().isInCache(identifier)
        && Objects.nonNull(
            getValueEncoding()
                .deserialize(identifier, payload, getValueEncoding().getDefaultFormat()));
  }

  @Override
  public CompletableFuture<T> patch(String id, Map<String, Object> partialData, String... path) {

    final Identifier identifier = Identifier.from(id, path);

    byte[] payload = getValueEncoding().serialize(modifyPatch(partialData));

    // validate
    if (!isUpdateValid(identifier, payload)) {
      throw new IllegalArgumentException("Partial update for ... not valid");
    }

    // TODO: SnapshotProvider???
    byte[] merged =
        getValueEncoding()
            .serialize(
                getValueEncoding()
                    .deserialize(identifier, payload, getValueEncoding().getDefaultFormat()));

    return getEventSourcing()
        .pushMutationEventRaw(identifier, merged)
        .whenComplete(
            (entityData, throwable) -> {
              if (Objects.nonNull(entityData)) {
                onUpdate(identifier, entityData);
              } else if (Objects.nonNull(throwable)) {
                onFailure(identifier, throwable);
              }
            });
  }

  protected Map<String, Object> modifyPatch(Map<String, Object> partialData) {
    return partialData;
  }
}
