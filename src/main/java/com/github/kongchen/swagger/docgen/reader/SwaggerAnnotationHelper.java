package com.github.kongchen.swagger.docgen.reader;

import io.swagger.annotations.*;
import io.swagger.converter.ModelConverters;
import io.swagger.models.Operation;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Tag;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

public class SwaggerAnnotationHelper {

    // TODO Review all the getters

    /*
    https://github.com/swagger-api/swagger-core/wiki/Annotations-1.5.X
    http://docs.swagger.io/swagger-core/current/apidocs/index.html?io/swagger/annotations

    -Api-                   basePath        produces        consumes
    ApiModel                ???
    ApiModelProperty        dataType        name
    ApiOperation            produces        consumes        response        responseContainer       responseReference
    ApiParam                format          name            type            collectionFormat
    ApiResponse             reference       response        responseContainer
    ApiResponses            ---
    ResponseHeader          ---
     */

    private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerAnnotationHelper.class);

    public List<SecurityRequirement> getSecurityRequirements(Api api) {
        List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
        for (Authorization auth : api.authorizations()) {
            if (auth.value().isEmpty()) {
                continue;
            }
            SecurityRequirement security = new SecurityRequirement();
            security.setName(auth.value());
            for (AuthorizationScope scope : auth.scopes()) {
                if (!scope.scope().isEmpty()) {
                    security.addScope(scope.scope());
                }
            }
            securities.add(security);
        }
        return securities;
    }

    public boolean isHidden(Api api) {
        return api.hidden();
    }

    public Set<Tag> extractTags(Api api) {
        Set<Tag> output = new LinkedHashSet<Tag>();

        boolean hasExplicitTags = false;
        for (String tag : api.tags()) {
            if (!tag.isEmpty()) {
                hasExplicitTags = true;
                output.add(new Tag().name(tag));
            }
        }
        if (!hasExplicitTags) {
            // derive tag from api path + description
            String tagString = api.value().replace("/", "");
            if (!tagString.isEmpty()) {
                Tag tag = new Tag().name(tagString);
                if (!api.description().isEmpty()) {
                    tag.description(api.description());
                }
                output.add(tag);
            }
        }
        return output;
    }

    public List<String> extractTags(ApiOperation apiOperation) {
        return Arrays.asList(apiOperation.tags());
    }

    public List<String> extractProtocols(ApiOperation apiOperation) {
        String[] protocols = apiOperation.protocols().split(",");
        return Arrays.asList(protocols);
    }

    public boolean isHidden(ApiOperation apiOperation) {
        return apiOperation.hidden();
    }

    public String getNickname(ApiOperation apiOperation) {
        return apiOperation.nickname();
    }

    public ResponseHeader[] getResponseHeaders(ApiOperation apiOperation) {
        return apiOperation.responseHeaders();
    }

    public int getResponseCode(ApiOperation apiOperation) {
        return apiOperation.code();
    }

    public String getHttpMethod(ApiOperation apiOperation) {
        return apiOperation.httpMethod();
    }

    public Extension[] getExtensions(ApiOperation apiOperation) {
        return apiOperation.extensions();
    }

    public Map<String, Property> parseResponseHeaders(ResponseHeader[] headers) {
        if (headers == null) {
            return null;
        }
        Map<String, Property> responseHeaders = null;
        for (ResponseHeader header : headers) {
            if (header.name().isEmpty()) {
                continue;
            }
            if (responseHeaders == null) {
                responseHeaders = new HashMap<String, Property>();
            }
            Class<?> cls = header.response();

            if (!cls.equals(Void.class) && !cls.equals(void.class)) {
                Property property = ModelConverters.getInstance().readAsProperty(cls);
                if (property != null) {
                    Property responseProperty;

                    if (header.responseContainer().equalsIgnoreCase("list")) {
                        responseProperty = new ArrayProperty(property);
                    } else if (header.responseContainer().equalsIgnoreCase("map")) {
                        responseProperty = new MapProperty(property);
                    } else {
                        responseProperty = property;
                    }
                    responseProperty.setDescription(header.description());
                    responseHeaders.put(header.name(), responseProperty);
                }
            }
        }
        return responseHeaders;
    }

    public void updateSummary(Operation operation, ApiOperation apiOperation) {
        operation.summary(apiOperation.value());
    }

    public void updateDescription(Operation operation, ApiOperation apiOperation) {
        operation.description(apiOperation.notes());
    }

    public Set<Map<String, Object>> parseCustomExtensions(Extension[] extensions) {
        if (extensions == null) {
            return Collections.emptySet();
        }
        Set<Map<String, Object>> resultSet = new HashSet<Map<String, Object>>();
        for (Extension extension : extensions) {
            if (extension == null) {
                continue;
            }
            Map<String, Object> extensionProperties = new HashMap<String, Object>();
            for (ExtensionProperty extensionProperty : extension.properties()) {
                String name = extensionProperty.name();
                if (!name.isEmpty()) {
                    String value = extensionProperty.value();
                    extensionProperties.put(name, value);
                }
            }
            if (!extension.name().isEmpty()) {
                Map<String, Object> wrapper = new HashMap<String, Object>();
                wrapper.put(extension.name(), extensionProperties);
                resultSet.add(wrapper);
            } else {
                resultSet.add(extensionProperties);
            }
        }
        return resultSet;
    }

    public Type getResponseType(Method method, ApiOperation apiOperation) {
        Type responseType = null;
        // TODO refactor this
        if(apiOperation != null) {
            responseType = apiOperation.response();
            boolean isVoid = (responseType.equals(Void.class) || responseType.equals(void.class));
            responseType = isVoid ? null : responseType;
        }

        if(responseType == null) {
            // pick out response from method declaration
            LOGGER.debug("picking up response class from method " + method);
            responseType = method.getGenericReturnType();
        }
        return responseType;
    }

    public String getResponseContainer(Method method, ApiOperation apiOperation) {
        String responseContainer = apiOperation.responseContainer();
        return apiOperation.responseContainer().isEmpty() ? null : responseContainer;
    }

    public List<SecurityRequirement> extractSecurities(ApiOperation apiOperation) {
        List<SecurityRequirement> securities = new ArrayList<SecurityRequirement>();
        for (Authorization auth : apiOperation.authorizations()) {
            if (!auth.value().isEmpty()) {
                SecurityRequirement security = new SecurityRequirement();
                security.setName(auth.value());
                for (AuthorizationScope scope : auth.scopes()) {
                    if (!scope.scope().isEmpty()) {
                        security.addScope(scope.scope());
                    }
                }
                securities.add(security);
            }
        }
        return securities;
    }
}
