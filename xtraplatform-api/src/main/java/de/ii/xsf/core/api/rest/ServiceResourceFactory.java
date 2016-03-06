/**
 * Copyright 2016 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ii.xsf.core.api.rest;

import de.ii.xsf.core.api.Service;
import io.dropwizard.views.View;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;

/**
 *
 * @author zahnen
 */
public interface ServiceResourceFactory {
    Class getServiceResourceClass();
    View getServicesView(Collection<Service> services, URI uri);
    Response getResponseForParams(Collection<Service> services, UriInfo uriInfo);
}
