package org.springdoc.webflux.ui;

import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.ui.AbstractSwaggerWelcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Spring Framework 7 compatibility patch for SpringDoc WebFlux UI.
 * Replaces the removed {@code UriComponentsBuilder.fromHttpRequest(HttpRequest)} with
 * {@code UriComponentsBuilder.fromUri(request.getURI())}.
 */
public abstract class SwaggerWelcomeCommon extends AbstractSwaggerWelcome {

    protected SwaggerWelcomeCommon(SwaggerUiConfigProperties swaggerUiConfig, SpringDocConfigProperties springDocConfigProperties) {
        super(swaggerUiConfig, springDocConfigProperties);
    }

    protected Mono<Void> redirectToUi(ServerHttpRequest request, ServerHttpResponse response) {
        SwaggerUiConfigParameters swaggerUiConfigParameters = new SwaggerUiConfigParameters(swaggerUiConfig);
        buildFromCurrentContextPath(swaggerUiConfigParameters, request);
        String prefix = springDocConfigProperties.getWebjars().getPrefix();
        String webjarsPrefix = "/webjars".equals(prefix) ? "" : prefix;
        String swaggerUiUrl = swaggerUiConfigParameters.getContextPath() + swaggerUiConfigParameters.getUiRootPath() + webjarsPrefix + getSwaggerUiUrl();
        UriComponentsBuilder uriComponentsBuilder = getUriComponentsBuilder(swaggerUiConfigParameters, swaggerUiUrl);
        request.getQueryParams().forEach(Objects.requireNonNull(uriComponentsBuilder)::queryParam);
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(uriComponentsBuilder.build().encode().toUri());
        return response.setComplete();
    }

    public Map<String, Object> getSwaggerUiConfig(ServerHttpRequest request) {
        SwaggerUiConfigParameters swaggerUiConfigParameters = new SwaggerUiConfigParameters(swaggerUiConfig);
        buildFromCurrentContextPath(swaggerUiConfigParameters, request);
        return swaggerUiConfigParameters.getConfigParameters();
    }

    void buildFromCurrentContextPath(SwaggerUiConfigParameters swaggerUiConfigParameters, ServerHttpRequest request) {
        super.init(swaggerUiConfigParameters);
        String contextPath = request.getPath().contextPath().value();
        swaggerUiConfigParameters.setContextPath(contextPath);
        // Spring 7 compatibility: fromUri instead of removed fromHttpRequest
        String uriString = UriComponentsBuilder.fromUri(request.getURI()).toUriString();
        String pathString = UriComponentsBuilder.fromPath(contextPath).toUriString();
        int i = uriString.indexOf(pathString) + pathString.length();
        if (i > 0) {
            uriString = uriString.substring(0, i);
        } else {
            uriString = request.getPath().toString();
        }
        buildConfigUrl(swaggerUiConfigParameters, UriComponentsBuilder.fromUriString(uriString));
    }

    @Override
    protected void calculateOauth2RedirectUrl(SwaggerUiConfigParameters swaggerUiConfigParameters, UriComponentsBuilder uriComponentsBuilder) {
        if (swaggerUiConfig.getOauth2RedirectUrl() == null || swaggerUiConfig.getOauth2RedirectUrl().isBlank() || !swaggerUiConfigParameters.isValidUrl(swaggerUiConfig.getOauth2RedirectUrl())) {
            String prefix = springDocConfigProperties.getWebjars().getPrefix();
            String webjarsPrefix = "/webjars".equals(prefix) ? "" : prefix;
            swaggerUiConfigParameters.setOauth2RedirectUrl(
                    uriComponentsBuilder
                            .path(swaggerUiConfigParameters.getUiRootPath())
                            .path(webjarsPrefix)
                            .path(getOauth2RedirectUrl())
                            .build()
                            .toString()
            );
        }
    }
}
