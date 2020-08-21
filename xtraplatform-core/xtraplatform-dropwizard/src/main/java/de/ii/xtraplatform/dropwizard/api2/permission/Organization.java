/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.api.permission;

import de.ii.xtraplatform.api.Resource;
import org.joda.time.DateTime;

/**
 *
 * @author fischer
 */
public class Organization implements Resource {

    private String name;
    private String description;
    private boolean active;
    private long expiration;
    private int serviceLimit;
    private String owner;

    public Organization() {
        this.active = true;
        this.expiration = 0;
        this.serviceLimit = 0;
        this.owner = "";
    }

    /**
     *
     * @return
     */
    //@JsonView(DefaultView.class)
    public String getName() {
        return name;
    }

    /**
     *
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return
     */
    @Override
    //@JsonView(DefaultView.class)
    public String getResourceId() {
        return name;
    }

    /**
     *
     * @param id
     */
    @Override
    public void setResourceId(String id) {
        this.name = id;
    }

    /**
     *
     * @return
     */
    //@JsonView(DefaultView.class)
    public String getDescription() {
        return description;
    }

    /**
     *
     * @param description
     */
    public void setDescription(String description) {
        this.description = description;
    }
    /**
     *
     * @return
     */
    //@JsonView(DefaultView.class)
    public boolean isActive() {
        return active;
    }

    /**
     *
     * @param active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     *
     * @return
     */
    //@JsonView(ConfigurationView.class)
    public long getExpiration() {
        return expiration;
    }

    /**
     *
     * @param expiration
     */
    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    /**
     *
     * @return
     */
    //@JsonView(AdminView.class)
    public int getExpiresIn() {
        // TODO: works only for values <= 365
        return expiration == 0 ? 0 : (new DateTime(expiration).minus(new DateTime().getMillis()).dayOfYear().get() - 1);
    }

    /**
     *
     * @param expiresIn
     */
    public void setExpiresIn(int expiresIn) {
        if (expiresIn > 0) {
            this.expiration = new DateTime().plusDays(expiresIn).getMillis();
        }
    }

    /**
     *
     * @return
     */
    //@JsonView(DefaultView.class)
    public int getServiceLimit() {
        return serviceLimit;
    }

    /**
     *
     * @param serviceLimit
     */
    public void setServiceLimit(int serviceLimit) {
        this.serviceLimit = serviceLimit;
    }

    /**
     *
     * @return
     */
    //@JsonView(ConfigurationView.class)
    public String getOwner() {
        return owner;
    }

    /**
     *
     * @param owner
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    
}