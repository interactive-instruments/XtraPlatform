/*
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.apache.felix.http.proxy.impl;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/** @since 3.0.0 */
public abstract class BridgeServiceTracker<T> extends ServiceTracker<T, T> {
  private static final String DEFAULT_FILTER = "(http.felix.dispatcher=*)";

  private volatile T usedService;

  public BridgeServiceTracker(final BundleContext context, final Class<?> objectClass)
      throws InvalidSyntaxException {
    super(context, createFilter(context, objectClass), null);
  }

  protected abstract void setService(final T service);

  protected abstract void unsetService();

  @Override
  public T addingService(final ServiceReference<T> ref) {
    final T service = super.addingService(ref);
    if (usedService == null) {
      this.usedService = service;
      this.setService(service);
    }

    return service;
  }

  @Override
  public void removedService(final ServiceReference<T> ref, final T service) {
    if (service == usedService) {
      this.unsetService();
    }

    super.removedService(ref, service);
  }

  private static Filter createFilter(final BundleContext context, final Class<?> objectClass)
      throws InvalidSyntaxException {
    StringBuffer str = new StringBuffer();
    str.append("(&(").append(Constants.OBJECTCLASS).append("=");
    str.append(objectClass.getName()).append(")");
    str.append(DEFAULT_FILTER).append(")");
    return context.createFilter(str.toString());
  }
}
