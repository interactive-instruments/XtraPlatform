/*
 * Copyright 2015-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.store.legacy.file;

import java.io.File;
import java.io.IOException;

/** Created by zahnen on 21.11.15. */
public class DeleteFileTransaction extends AbstractFileTransaction {

  public DeleteFileTransaction(File file) {
    super(file);
  }

  @Override
  public void execute() throws IOException {
    backup();
    file.delete();
  }
}
