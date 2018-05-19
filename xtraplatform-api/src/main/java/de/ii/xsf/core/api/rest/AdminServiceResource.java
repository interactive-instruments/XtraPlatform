/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xsf.core.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.xsf.core.api.Service;
import de.ii.xsf.core.api.ServiceRegistry;
import de.ii.xsf.core.api.exceptions.XtraserverFrameworkException;
import de.ii.xsf.core.api.permission.Auth;
import de.ii.xsf.core.api.permission.AuthenticatedUser;
import de.ii.xsf.core.api.permission.AuthorizationProvider;
import de.ii.xsf.logging.XSFLogger;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.slf4j.MDC;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Map;

/**
 *
 * @author zahnen
 */
abstract public class AdminServiceResource implements ServiceResource {

    private static final String OPERATION_KEY = "_operation_";
    private static final String OPERATION_PARAMETER_KEY = "_parameter_";
    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(AdminServiceResource.class);
    protected ObjectMapper jsonMapper;
    protected AuthorizationProvider permissions;
    //private Services services;
    protected ServiceRegistry serviceRegistry;
    protected Service service = null;
    @Context
    protected UriInfo uriInfo;

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public void setService(Service service) {
        this.service = service;
    }

    public void init(ObjectMapper jsonMapper, ServiceRegistry serviceRegistry, AuthorizationProvider permissions) {
        this.jsonMapper = jsonMapper;
        this.serviceRegistry = serviceRegistry;
        this.permissions = permissions;
    }

    @DELETE
    public Response deleteService(@Auth(required = false) AuthenticatedUser authUser, @PathParam("id") String id) {
        try {
            MDC.put("service", id);
            serviceRegistry.deleteService(authUser, service);
            //LOGGER.info(DELETED_SERVICE_WTH_ID, id);
            return Response.ok().build();
        } finally {
            MDC.remove("service");
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateOrCallServiceOperation(/*@Auth(required = false) AuthenticatedUser authUser,*/ @PathParam("id") String id, String request) {
        try {
            MDC.put("service", id);
            try {
                Map<String, String> req = jsonMapper.readValue(request, Map.class);
                LOGGER.getLogger().debug(request);
                if (req.containsKey(OPERATION_KEY)) {
                    String operation = req.get(OPERATION_KEY);
                    String parameter = req.containsKey(OPERATION_PARAMETER_KEY) ? req.get(OPERATION_PARAMETER_KEY) : "";
                    if (operation.equals("start")) {
                        service.start();
                    } else if (operation.equals("stop")) {
                        service.stop();
                    } else {
                        callServiceOperation(/*authUser*/new AuthenticatedUser(), operation, parameter);
                    }
                    return Response.ok().build();
                }
            } catch (IOException ex) {
                throw new XtraserverFrameworkException();
            }

            updateService(/*authUser*/new AuthenticatedUser(), id, request);
            return Response.ok().build();
            
        } finally {
            MDC.remove("service");
        }
    }

    abstract protected void callServiceOperation(AuthenticatedUser authUser, String operation, String parameter);

    abstract protected void updateService(AuthenticatedUser authUser, String id, String request);
}
