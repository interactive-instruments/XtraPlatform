/*
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.di.domain;

import de.ii.xtraplatform.runtime.domain.LogContext;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.Factory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author zahnen */
public final class FactoryRegistryState<T> implements Registry.State<Factory>, FactoryRegistry<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FactoryRegistryState.class);

  private final String className;
  private final BundleContext bundleContext;
  private final Registry.State<Factory> factories;
  private final Map<T, ComponentInstance> instances;

  public FactoryRegistryState(
      String className,
      String shortName,
      BundleContext bundleContext,
      String... componentProperties) {
    this.className = className;
    this.bundleContext = bundleContext;
    this.factories =
        new RegistryState<>(
            String.format("%s factory", shortName), bundleContext, componentProperties);
    this.instances = new ConcurrentHashMap<>();
  }

  @Override
  public Collection<Factory> get() {
    return factories.get();
  }

  @Override
  public Optional<Factory> get(String... identifiers) {
    return factories.get(identifiers);
  }

  @Override
  public Optional<Factory> onArrival(ServiceReference<Factory> ref) {
    return factories.onArrival(ref);
  }

  @Override
  public Optional<Factory> onDeparture(ServiceReference<Factory> ref) {
    return factories.onDeparture(ref);
  }

  @Override
  public boolean ensureTypeExists() {
    try {
      bundleContext.getBundle().loadClass(className);
      return true;
    } catch (Throwable e) {
      LogContext.error(LOGGER, e, "Factory target class '{}' does not exist", className);
    }
    return false;
  }

  @Override
  public T createInstance(Map<String, Object> configuration, String... factoryProperties) {
    final Optional<Factory> factory = factories.get(factoryProperties);

    if (!factory.isPresent()) {
      throw new IllegalStateException();
    }

    try {
      ComponentInstance connectorInstance =
          factory.get().createComponentInstance(new Hashtable<>(configuration));
      ServiceReference<?>[] connectorRefs =
          bundleContext.getServiceReferences(
              className, "(instance.name=" + connectorInstance.getInstanceName() + ")");
      T connector = (T) bundleContext.getService(connectorRefs[0]);

      instances.put(connector, connectorInstance);

      return connector;

    } catch (Throwable e) {
      throw new IllegalStateException("", e);
    }
  }

  @Override
  public void disposeInstance(T instance) {
    if (instances.containsKey(instance)) {
      ComponentInstance componentInstance = instances.remove(instance);
      componentInstance.dispose();
    }
  }
}
