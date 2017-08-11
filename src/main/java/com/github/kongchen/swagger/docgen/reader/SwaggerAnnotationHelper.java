package com.github.kongchen.swagger.docgen.reader;

import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SwaggerAnnotationHelper {

    /*
    https://github.com/swagger-api/swagger-core/wiki/Annotations-1.5.X
    http://docs.swagger.io/swagger-core/current/apidocs/index.html?io/swagger/annotations

    Api                     basePath        produces        consumes
    ApiModel                ???
    ApiModelProperty        dataType        name
    ApiOperation            produces        consumes        response        responseContainer       responseReference
    ApiParam                format          name            type            collectionFormat
    ApiResponse             reference       response        responseContainer
    ApiResponses            ---
    ResponseHeader          ---
     */

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
}
