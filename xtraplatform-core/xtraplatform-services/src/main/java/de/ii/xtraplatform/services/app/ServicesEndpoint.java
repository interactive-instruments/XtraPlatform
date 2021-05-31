/*
 * Copyright 2015-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.services.app;

import akka.http.scaladsl.model.headers.LinkParams.type;
import de.ii.xtraplatform.dropwizard.domain.Dropwizard;
import de.ii.xtraplatform.dropwizard.domain.Endpoint;
import de.ii.xtraplatform.dropwizard.domain.MediaTypeCharset;
import de.ii.xtraplatform.dropwizard.domain.XtraPlatform;
import de.ii.xtraplatform.runtime.domain.LogContext;
import de.ii.xtraplatform.services.domain.Service;
import de.ii.xtraplatform.services.domain.ServiceData;
import de.ii.xtraplatform.services.domain.ServiceEndpoint;
import de.ii.xtraplatform.services.domain.ServiceInjectableContext;
import de.ii.xtraplatform.services.domain.ServiceListingProvider;
import de.ii.xtraplatform.store.domain.entities.EntityRegistry;
import io.dropwizard.jersey.caching.CacheControl;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.whiteboard.Wbp;
import org.apache.felix.ipojo.whiteboard.Whiteboards;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author zahnen */
@Component
@Provides
@Instantiate
@Whiteboards(
    whiteboards = {
      @Wbp(
          filter = "(objectClass=de.ii.xtraplatform.services.domain.ServiceEndpoint)",
          onArrival = "onServiceResourceArrival",
          onDeparture = "onServiceResourceDeparture"),
      @Wbp(
          filter = "(objectClass=de.ii.xtraplatform.services.domain.ServiceListingProvider)",
          onArrival = "onServiceListingProviderArrival",
          onDeparture = "onServiceListingProviderDeparture")
    })
@Hidden
@Path("/services/")
@Produces(MediaTypeCharset.APPLICATION_JSON_UTF8)
public class ServicesEndpoint implements Endpoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServicesEndpoint.class);

  private final BundleContext bundleContext;
  private final EntityRegistry entityRegistry;
  private final ServiceInjectableContext serviceContext;
  private final Dropwizard dropwizard;
  private final XtraPlatform xtraPlatform;

  private Map<String, ServiceEndpoint> serviceResources;
  private Map<MediaType, ServiceListingProvider> serviceListingProviders;

  public ServicesEndpoint(
      @org.apache.felix.ipojo.annotations.Context BundleContext bundleContext,
      @Requires EntityRegistry entityRegistry,
      @Requires Dropwizard dropwizard,
      @Requires XtraPlatform xtraPlatform,
      @Requires ServiceInjectableContext serviceContext) {
    this.serviceResources = new LinkedHashMap<>();
    this.serviceListingProviders = new LinkedHashMap<>();
    this.bundleContext = bundleContext;
    this.entityRegistry = entityRegistry;
    this.xtraPlatform = xtraPlatform;
    this.dropwizard = dropwizard;
    this.serviceContext = serviceContext;
  }

  public synchronized void onServiceResourceArrival(ServiceReference<ServiceEndpoint> ref) {
    ServiceEndpoint sr = bundleContext.getService(ref);
    String type = (String) ref.getProperty(ServiceEndpoint.SERVICE_TYPE_KEY);
    if (sr != null && type != null) {
      serviceResources.put(type, sr);
    }
  }

  public synchronized void onServiceResourceDeparture(ServiceReference<ServiceEndpoint> ref) {
    ServiceEndpoint sr = bundleContext.getService(ref);
    String type = (String) ref.getProperty(ServiceEndpoint.SERVICE_TYPE_KEY);
    if (sr != null && type != null) {
      serviceResources.remove(type);
    }
  }

  public synchronized void onServiceListingProviderArrival(
      ServiceReference<ServiceListingProvider> ref) {
    ServiceListingProvider serviceListingProvider = bundleContext.getService(ref);
    if (serviceListingProvider != null) {
      MediaType type = serviceListingProvider.getMediaType();
      if (type != null) {
        serviceListingProviders.put(type, serviceListingProvider);
      }
    }
  }

  public synchronized void onServiceListingProviderDeparture(
      ServiceReference<ServiceListingProvider> ref) {
    ServiceListingProvider serviceListingProvider = bundleContext.getService(ref);
    if (serviceListingProvider != null) {
      MediaType type = serviceListingProvider.getMediaType();
      if (type != null) {
        serviceListingProviders.remove(type);
      }
    }
  }

  // TODO
  @GET
  @Produces(MediaType.WILDCARD)
  public Response getServices(
      @QueryParam("callback") String callback,
      @QueryParam("f") String f,
      @Context UriInfo uriInfo,
      @Context ContainerRequestContext containerRequestContext) {
    openLoggingContext(containerRequestContext);

    List<ServiceData> services =
        entityRegistry.getEntitiesForType(Service.class).stream()
            .map(Service::getData)
            .filter(serviceData -> !serviceData.hasError())
            .collect(Collectors.toList());

    Set<MediaType> supportedMediaTypes = serviceListingProviders.keySet();
    List<MediaType> acceptableMediaTypes = containerRequestContext.getAcceptableMediaTypes();
    Optional<MediaType> negotiatedType = Optional.empty();
    for (MediaType type : acceptableMediaTypes) {
      negotiatedType = supportedMediaTypes.stream().filter(type2 -> type2.isCompatible(type)).findFirst();
      if (negotiatedType.isPresent())
        break;
    }

    MediaType mediaType =
        Objects.equals(f, "json")
            ? MediaType.APPLICATION_JSON_TYPE
            : Objects.equals(f, "html")
                ? MediaType.TEXT_HTML_TYPE
                : negotiatedType.orElseGet(() ->
                    Objects.nonNull(containerRequestContext.getMediaType())
                        && serviceListingProviders
                        .containsKey(containerRequestContext.getMediaType())
                        ? containerRequestContext.getMediaType()
                        : containerRequestContext.getHeaderString("user-agent").toLowerCase()
                            .contains("google-site-verification")
                            ? MediaType.TEXT_HTML_TYPE
                            : MediaType.APPLICATION_JSON_TYPE);

    if (serviceListingProviders.containsKey(mediaType)) {
      Response serviceListing =
          serviceListingProviders
              .get(mediaType)
              .getServiceListing(services, uriInfo.getRequestUri());
      return Response.ok().entity(serviceListing.getEntity()).type(mediaType).build();
    }

    return Response.ok().entity(services).build();
  }

  // TODO
  @GET
  @Path("/___static___/{file: .+}")
  @Produces(MediaType.WILDCARD)
  @CacheControl(maxAge = 3600)
  public Response getFile(@PathParam("file") String file) {
    // LOGGER.debug("FILE {})", file);

    if (serviceListingProviders.containsKey(MediaType.TEXT_HTML_TYPE)) {
      return serviceListingProviders.get(MediaType.TEXT_HTML_TYPE).getStaticAsset(file);
    }

    return Response.status(Response.Status.NOT_FOUND).build();
  }

  @Path("/{service}/")
  public ServiceEndpoint getServiceResource(
      @PathParam("service") String id,
      @QueryParam("callback") String callback,
      @Context ContainerRequestContext containerRequestContext) {
    return getVersionedServiceResource(id, callback, containerRequestContext, null);
  }

  @Path("/{service}/v{version}/")
  public ServiceEndpoint getVersionedServiceResource(
      @PathParam("service") String id,
      @QueryParam("callback") String callback,
      @Context ContainerRequestContext containerRequestContext,
      @PathParam("version") Integer version) {

    Service service = getService(id, callback);

    if (service.getData().getApiVersion().isPresent()) {
      Integer apiVersion = service.getData().getApiVersion().get();

      if (Objects.isNull(version)) {
        String redirectPath =
            containerRequestContext
                .getUriInfo()
                .getAbsolutePath()
                .getPath()
                .replace(id, String.format("%s/v%d", id, apiVersion));
        if (getExternalUri().isPresent()) {
          redirectPath = redirectPath.replace("/rest/services", getExternalUri().get().getPath());
        }
        URI redirectUri =
            containerRequestContext
                .getUriInfo()
                .getRequestUriBuilder()
                .replacePath(redirectPath)
                .build();

        throw new WebApplicationException(Response.temporaryRedirect(redirectUri).build());
      } else if (!Objects.equals(apiVersion, version)) {
        throw new NotFoundException();
      }
    } else if (Objects.nonNull(version)) {
      throw new NotFoundException();
    }

    openLoggingContext(id, version, containerRequestContext);

    serviceContext.inject(containerRequestContext, service);

    return getServiceResource(service);
  }

  // TODO: after switch to jersey 2.x, use Resource.from and move instantiation to factory
  // TODO: cache resource object per service
  private ServiceEndpoint getServiceResource(Service s) {
    return serviceResources.get(s.getServiceType());
  }

  private Service getService(String id, String callback) {
    Optional<Service> s = entityRegistry.getEntity(Service.class, id);

    if (!s.isPresent() || s.get().getData().hasError()) {
      throw new NotFoundException();
    }

    return s.get();
  }

  private Optional<URI> getExternalUri() {
    return Optional.ofNullable(xtraPlatform.getServicesUri());
  }

  private void openLoggingContext(ContainerRequestContext containerRequestContext) {
    openLoggingContext(null, null, containerRequestContext);
  }

  private void openLoggingContext(
      String serviceId, Integer version, ContainerRequestContext containerRequestContext) {
    if (Objects.nonNull(serviceId)) {
      LogContext.put(LogContext.CONTEXT.SERVICE, serviceId);
    } else {
      LogContext.remove(LogContext.CONTEXT.SERVICE);
    }

    if (LOGGER.isDebugEnabled()) {
      LogContext.put(LogContext.CONTEXT.REQUEST, LogContext.generateRandomUuid().toString());
      LOGGER.debug(
          "Processing request: {} {}",
          containerRequestContext.getMethod(),
          formatUri(containerRequestContext.getUriInfo().getRequestUri(), serviceId, version));
    } else {
      LogContext.remove(LogContext.CONTEXT.REQUEST);
    }
  }

  private static String formatUri(URI uri, String serviceId, Integer version) {
    String path = uri.getPath();

    if (Objects.nonNull(serviceId)) {
      path = path.substring(path.indexOf(serviceId) + serviceId.length());
    }
    if (Objects.nonNull(version)) {
      String versionString = "v" + version;
      path = path.substring(path.indexOf(versionString) + versionString.length());
    }

    if (path.isEmpty()) {
      path = "/";
    }

    if (uri.getQuery() == null) {
      return path;
    }

    return path + "?" + uri.getQuery();
  }
}
