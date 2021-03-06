/*
 * Copyright 2019-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.dropwizard.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import de.ii.xtraplatform.dropwizard.app.MergingSourceProvider;
import de.ii.xtraplatform.dropwizard.app.XtraServerFrameworkCommand;
import de.ii.xtraplatform.runtime.domain.Constants;
import de.ii.xtraplatform.runtime.domain.XtraPlatformConfiguration;
import io.dropwizard.Application;
import io.dropwizard.cli.Cli;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.JarLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConfigurationProvider<T extends XtraPlatformConfiguration>
    implements ApplicationProvider<T>, ConfigurationProvider<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConfigurationProvider.class);
  private static final String DW_CMD = "server";

  private final CompletableFuture<T> configuration = new CompletableFuture<>();
  private final CompletableFuture<Environment> environment = new CompletableFuture<>();

  @Override
  public T getConfiguration() {
    try {
      return configuration.get();
    } catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }

  @Override
  public Optional<ByteSource> getConfigurationFileTemplate(String environment) {
    return getConfigurationFileTemplateFromClassBundle(
        environment, AbstractConfigurationProvider.class);
  }

  private Optional<ByteSource> getConfigurationFileTemplateFromClassBundle(
      String environment, Class<?> clazz) {
    String cfgFileTemplateName = String.format("/cfg.%s.yml", environment);
    ByteSource byteSource = null;
    try {
      byteSource = Resources.asByteSource(Resources.getResource(clazz, cfgFileTemplateName));
    } catch (Throwable e) {
      // ignore
    }
    return Optional.ofNullable(byteSource);
  }

  @Override
  public Pair<T, Environment> startWithFile(
      Path configurationFile, Constants.ENV env, Consumer<Bootstrap<T>> initializer) {
    Bootstrap<T> bootstrap = getBootstrap(initializer, env);

    return run(configurationFile.toString(), bootstrap);
  }

  private Pair<T, Environment> run(String configurationFilePath, Bootstrap<T> bootstrap) {
    final Cli cli = new Cli(new JarLocation(getClass()), bootstrap, System.out, System.err);
    String[] arguments = {DW_CMD, configurationFilePath};

    try {
      if (cli.run(arguments).isEmpty()) {
        T cfg = configuration.get(30, TimeUnit.SECONDS);
        Environment env = environment.get(30, TimeUnit.SECONDS);

        return new ImmutablePair<>(cfg, env);
      }
    } catch (Exception e) {
      // continue
    }

    throw new IllegalStateException();
  }

  private Bootstrap<T> getBootstrap(Consumer<Bootstrap<T>> initializer, Constants.ENV env) {
    Application<T> application =
        new Application<T>() {
          @Override
          public void run(T configuration, Environment environment) throws Exception {
            AbstractConfigurationProvider.this.configuration.complete(configuration);
            AbstractConfigurationProvider.this.environment.complete(environment);
          }
        };

    Bootstrap<T> bootstrap = new Bootstrap<>(application);
    bootstrap.addCommand(new XtraServerFrameworkCommand<>(application));

    bootstrap.setConfigurationSourceProvider(
        new SubstitutingSourceProvider(
            new MergingSourceProvider(
                bootstrap.getConfigurationSourceProvider(), getAdditionalBaseConfigs(), env),
            new EnvironmentVariableSubstitutor(false)));

    initializer.accept(bootstrap);

    bootstrap.registerMetrics();

    return bootstrap;
  }

  public List<ByteSource> getAdditionalBaseConfigs() {
    return ImmutableList.of();
  }
}
