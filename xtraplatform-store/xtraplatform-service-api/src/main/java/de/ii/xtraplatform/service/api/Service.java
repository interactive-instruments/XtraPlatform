/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.service.api;

import de.ii.xtraplatform.entity.api.PersistentEntity;

/**
 * @author zahnen
 */
public interface Service extends PersistentEntity {

    String ENTITY_TYPE = "services";

    @Override
    default String getType() {
        return ENTITY_TYPE;
    }

    default String getServiceType() {
        return getData().getServiceType();
    }

    @Override
    ServiceData getData();
}
