package de.ii.xtraplatform.auth.internal.infra.rest;

import de.ii.xtraplatform.dropwizard.api.AuthConfig;
import de.ii.xtraplatform.auth.api.ImmutableUser;
import de.ii.xtraplatform.auth.api.Role;
import de.ii.xtraplatform.auth.api.TokenHandler;
import de.ii.xtraplatform.auth.api.User;
import de.ii.xtraplatform.auth.api.UserAuthenticator;
import de.ii.xtraplatform.auth.internal.SplitCookie;
import de.ii.xtraplatform.auth.internal.domain.ImmutableTokenResponse;
import de.ii.xtraplatform.dropwizard.api.XtraPlatform;
import de.ii.xtraplatform.web.api.Endpoint;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Provides
@Instantiate
@Path("/auth")
public class TokenEndpoint implements Endpoint {

    private static final int DEFAULT_EXPIRY = 2592000;

    private final UserAuthenticator authenticator;
    private final TokenHandler tokenGenerator;
    private final XtraPlatform xtraPlatform;
    private final AuthConfig authConfig;

    public TokenEndpoint(@Requires UserAuthenticator authenticator, @Requires TokenHandler tokenGenerator, @Requires XtraPlatform xtraPlatform) {
        this.authenticator = authenticator;
        this.tokenGenerator = tokenGenerator;
        this.xtraPlatform = xtraPlatform;
        this.authConfig = xtraPlatform.getConfiguration().auth;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/token")
    public Response authorize(@Context HttpServletRequest request,
                              Map<String, String> body) throws IOException {

        Optional<User> user;

        //TODO: get from cfg.yml, makes it easier to set different defaults for different products
        if (authConfig.isAnonymousAccessAllowed) {
            user = Optional.of(ImmutableUser.builder()
                                            .name("admin")
                                            .role(Role.ADMIN)
                                            .build());
        } else {
             user = authenticator.authenticate(body.get("user"), body.get("password"));
        }

        if (!user.isPresent()) {
            return Response.ok()
                           .build();
        }

        int expiresIn = Optional.ofNullable(body.get("expiration"))
                                .map(exp -> {
                                    try {
                                        return Integer.parseInt(exp);
                                    } catch (NumberFormatException e) {
                                        // so we use our default
                                    }
                                    return null;
                                })
                                .orElse(DEFAULT_EXPIRY);

        boolean rememberMe = Boolean.parseBoolean(body.get("rememberMe"));

        String token = tokenGenerator.generateToken(user.get(), expiresIn, rememberMe);


        Response.ResponseBuilder response = Response.ok()
                                                    .entity(ImmutableTokenResponse.builder()
                                                                                  .access_token(token)
                                                                                  .expires_in(expiresIn)
                                                                                  .build());

        String domain = null;//request.getServerName();

        List<String> authCookies = SplitCookie.writeToken(token, domain, isSecure(), rememberMe);

        authCookies.forEach(cookie -> response.header("Set-Cookie", cookie));

        return response.build();
    }

    //TODO: instead of external url, get request url
    //TODO: but we want to access view action links with same token, would that work?
    private String getDomain() {
        return getExternalUri().getHost();
    }

    // TODO: even if external url is set, we might want to access manager via http://localhost
    private boolean isSecure() {
        return false;
        //return Objects.equals(getExternalUri().getScheme(), "https");
    }

    private URI getExternalUri() {
        return xtraPlatform.getServicesUri();
    }
}