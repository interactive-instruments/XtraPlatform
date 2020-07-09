/**
 * Copyright 2018 interactive instruments GmbH
 * <p>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.event.store;

import de.ii.xtraplatform.entity.api.Mergeable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author zahnen
 */
public interface MergeableKeyValueStore<T extends Mergeable> extends KeyValueStore<T> {

    CompletableFuture<T> patch(String id, Map<String, Object> partialData, String... path);

    <U extends T> MergeableKeyValueStore<U> forType(Class<U> type);

}