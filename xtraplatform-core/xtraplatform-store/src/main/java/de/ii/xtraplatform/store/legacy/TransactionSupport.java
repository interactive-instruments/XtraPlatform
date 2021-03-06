/*
 * Copyright 2015-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.store.legacy;

/** @author zahnen */
public interface TransactionSupport<T> {
  /**
   * @param key the key
   * @return a new transaction
   */
  Transaction openDeleteTransaction(String key);

  /**
   * @param key the key
   * @return a new transaction
   */
  WriteTransaction<T> openWriteTransaction(String key);
}
