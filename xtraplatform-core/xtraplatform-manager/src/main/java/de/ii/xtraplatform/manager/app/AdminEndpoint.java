/*
 * Copyright 2015-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.manager.app;

import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.dropwizard.domain.Endpoint;
import de.ii.xtraplatform.dropwizard.domain.MediaTypeCharset;
import io.dropwizard.jersey.caching.CacheControl;
import java.util.Collection;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author zahnen */
@Component
@Provides
@Instantiate
@Path("/admin/")
@Produces(MediaTypeCharset.APPLICATION_JSON_UTF8)
public class AdminEndpoint implements Endpoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminEndpoint.class);

  private String xsfVersion = "todo";

  @GET
  @CacheControl(noCache = true)
  public AdminRoot getAdmin() {
    return new AdminRoot(xsfVersion);
  }

  @Path("/servicetypes")
  @GET
  @CacheControl(noCache = true)
  public Collection getAdminServiceTypes() {
    return ImmutableList.of(); // TODO serviceRegistry.getServiceTypes();
  }
}
