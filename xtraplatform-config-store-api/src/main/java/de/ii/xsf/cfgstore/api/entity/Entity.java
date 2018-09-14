/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xsf.cfgstore.api.entity;

import de.ii.xsf.core.api.Resource;

/**
 * @author zahnen
 */
public interface Entity<T extends EntityConfiguration> extends Resource {
    //TODO protected
    T getData();
}