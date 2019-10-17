/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.cfgstore.api;

import java.io.IOException;

/**
 * Created by zahnen on 27.11.15.
 */
public interface BundleConfig {
    void save() throws IOException;
}