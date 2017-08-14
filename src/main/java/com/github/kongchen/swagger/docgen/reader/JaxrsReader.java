package com.github.kongchen.swagger.docgen.reader;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.converter.ModelConverters;
import io.swagger.jaxrs.ext.SwaggerExtension;
import io.swagger.jaxrs.ext.SwaggerExtensions;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.ReflectionUtils;
import org.apache.maven.plugin.logging.Log;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import javax.ws.rs.*;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class JaxrsReader extends AbstractReader implements ClassSwaggerReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(JaxrsReader.class);
    private static final ResponseContainerConverter RESPONSE_CONTAINER_CONVERTER = new ResponseContainerConverter();
    private static final String CLASS_NAME_PREFIX = "class ";
    private static final String INTERFACE_NAME_PREFIX = "interface ";

    public JaxrsReader(Swagger swagger, Log LOG) {
        super(swagger, LOG);
    }

    @Override
    public Swagger read(Set<Class<?>> classes) {
        for (Class<?> cls : classes) {
            read(cls);
        }
        return swagger;
    }

    public Swagger getSwagger() {
        return swagger;
    }

    public Swagger read(Class<?> cls) {
        return read(cls, "", null, false, new String[0], new String[0], new HashMap<String, Tag>(), new ArrayList<Parameter>());
    }

    protected Swagger read(Class<?> cls, String parentPath, String parentMethod, boolean readHidden, String[] parentConsumes, String[] parentProduces, Map<String, Tag> parentTags, List<Parameter> parentParameters) {
        if (swagger == null) {
            swagger = new Swagger();
        }
        Api api = AnnotationUtils.findAnnotation(cls, Api.class);
        Path apiPath = AnnotationUtils.findAnnotation(cls, Path.class);

        // only read if allowing hidden apis OR api is not marked as hidden
        if (!canReadApi(readHidden, api)) {
            return swagger;
        }

        Map<String, Tag> tags = updateTagsForApi(parentTags, api);
        List<SecurityRequirement> securities = swaggerAnnotationHelper.getSecurityRequirements(api);

        // merge consumes, pro duces

        // look for method-level annotated properties

        // handle subresources by looking at return type

        // parse the method
        for (Method method : cls.getMethods()) {
            ApiOperation apiOperation = AnnotationUtils.findAnnotation(method, ApiOperation.class);
            if (apiOperation == null || swaggerAnnotationHelper.isHidden(apiOperation)) {
                continue;
            }
            Path methodPath = AnnotationUtils.findAnnotation(method, Path.class);

            String operationPath = getPath(apiPath, methodPath, parentPath);
            if (operationPath != null) {
                Map<String, String> regexMap = new HashMap<String, String>();
                operationPath = parseOperationPath(operationPath, regexMap);

                String httpMethod = extractOperationMethod(apiOperation, method, SwaggerExtensions.chain());

                Operation operation = parseMethod(method);
                updateOperationParameters(parentParameters, regexMap, operation);
                updateOperationProtocols(apiOperation, operation);

                String[] apiConsumes = new String[0];
                String[] apiProduces = new String[0];

                Consumes consumes = AnnotationUtils.findAnnotation(cls, Consumes.class);
                if (consumes != null) {
                    apiConsumes = consumes.value();
                }
                Produces produces = AnnotationUtils.findAnnotation(cls, Produces.class);
                if (produces != null) {
                    apiProduces = produces.value();
                }

                apiConsumes = updateOperationConsumes(parentConsumes, apiConsumes, operation);
                apiProduces = updateOperationProduces(parentProduces, apiProduces, operation);

                handleSubResource(apiConsumes, httpMethod, apiProduces, tags, method, operationPath, operation);

                // can't continue without a valid http method
                httpMethod = (httpMethod == null) ? parentMethod : httpMethod;
                updateTagsForOperation(operation, apiOperation);
                updateOperation(apiConsumes, apiProduces, tags, securities, operation);
                updatePath(operationPath, httpMethod, operation);
            }
            updateTagDescriptions();
        }

        return swagger;
    }

    private void updateTagDescriptions() {
        HashMap<String, Tag> tags = new HashMap<String, Tag>();
        for (Class<?> aClass: new Reflections("").getTypesAnnotatedWith(SwaggerDefinition.class)) {
            SwaggerDefinition swaggerDefinition = AnnotationUtils.findAnnotation(aClass, SwaggerDefinition.class);

            for (io.swagger.annotations.Tag tag : swaggerDefinition.tags()) {

                String tagName = tag.name();
                if (!tagName.isEmpty()) {
                    tags.put(tag.name(), new Tag().name(tag.name()).description(tag.description()));
                }
            }
        }
        if (swagger.getTags() != null) {
            for (Tag tag : swagger.getTags()) {
                Tag rightTag = tags.get(tag.getName());
                if (rightTag != null && rightTag.getDescription() != null) {
                    tag.setDescription(rightTag.getDescription());
                }
            }
        }
    }

    private void handleSubResource(String[] apiConsumes, String httpMethod, String[] apiProduces, Map<String, Tag> tags, Method method, String operationPath, Operation operation) {
        if (isSubResource(method)) {
            Class<?> responseClass = method.getReturnType();
            read(responseClass, operationPath, httpMethod, true, apiConsumes, apiProduces, tags, operation.getParameters());
        }
    }

    protected boolean isSubResource(Method method) {
        Class<?> responseClass = method.getReturnType();
        return (responseClass != null) && (AnnotationUtils.findAnnotation(responseClass, Api.class) != null);
    }

    private String getPath(Path classLevelPath, Path methodLevelPath, String parentPath) {
        if (classLevelPath == null && methodLevelPath == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        if (parentPath != null && !parentPath.isEmpty() && !parentPath.equals("/")) {
            if (!parentPath.startsWith("/")) {
                parentPath = "/" + parentPath;
            }
            if (parentPath.endsWith("/")) {
                parentPath = parentPath.substring(0, parentPath.length() - 1);
            }

            stringBuilder.append(parentPath);
        }
        if (classLevelPath != null) {
            stringBuilder.append(classLevelPath.value());
        }
        if (methodLevelPath != null && !methodLevelPath.value().equals("/")) {
            String methodPath = methodLevelPath.value();
            if (!methodPath.startsWith("/") && !stringBuilder.toString().endsWith("/")) {
                stringBuilder.append("/");
            }
            if (methodPath.endsWith("/")) {
                methodPath = methodPath.substring(0, methodPath.length() - 1);
            }
            stringBuilder.append(methodPath);
        }
        String output = stringBuilder.toString();
        if (!output.startsWith("/")) {
            output = "/" + output;
        }
        if (output.endsWith("/") && output.length() > 1) {
            return output.substring(0, output.length() - 1);
        } else {
            return output;
        }
    }


    public Operation parseMethod(Method method) {
        Operation operation = new Operation();
        ApiOperation apiOperation = AnnotationUtils.findAnnotation(method, ApiOperation.class);
        int responseCode = swaggerAnnotationHelper.getResponseCode(apiOperation);

        String operationId = method.getName();
        String responseContainer = null;

        Map<String, Property> defaultResponseHeaders = null;

        if (apiOperation != null) {
            if (swaggerAnnotationHelper.isHidden(apiOperation)) {
                return null;
            }
            String nickname = swaggerAnnotationHelper.getNickname(apiOperation);
            if (!nickname.isEmpty()) {
                operationId = nickname;
            }

            defaultResponseHeaders = parseResponseHeaders(swaggerAnnotationHelper.getResponseHeaders(apiOperation));
            swaggerAnnotationHelper.updateSummary(operation, apiOperation);
            swaggerAnnotationHelper.updateDescription(operation, apiOperation);

            Set<Map<String, Object>> customExtensions = parseCustomExtensions(swaggerAnnotationHelper.getExtensions(apiOperation));
            if (customExtensions != null) {
                for (Map<String, Object> extension : customExtensions) {
                    if (extension == null) {
                        continue;
                    }
                    for (Map.Entry<String, Object> map : extension.entrySet()) {
                        operation.setVendorExtension(map.getKey().startsWith("x-") ? map.getKey() : "x-" + map.getKey(), map.getValue());
                    }
                }
            }

            responseContainer = swaggerAnnotationHelper.getResponseContainer(method, apiOperation);

            List<SecurityRequirement> securities = swaggerAnnotationHelper.extractSecurities(apiOperation);

            for (SecurityRequirement sec : securities) {
                operation.security(sec);
            }
        }
        operation.operationId(operationId);

        Type responseClassType = swaggerAnnotationHelper.getResponseType(method, apiOperation);

        if ((responseClassType != null)
                && !responseClassType.equals(Void.class)
                && !responseClassType.equals(void.class)
                && !responseClassType.equals(javax.ws.rs.core.Response.class)
                && (AnnotationUtils.findAnnotation(convertToClass(responseClassType), Api.class) == null)) {
            if (isPrimitive(responseClassType)) {
                Property property = ModelConverters.getInstance().readAsProperty(responseClassType);
                if (property != null) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, property);

                    operation.response(responseCode, new Response()
                            .description("successful operation")
                            .schema(responseProperty)
                            .headers(defaultResponseHeaders));
                }
            } else if (!responseClassType.equals(Void.class) && !responseClassType.equals(void.class)) {
                Map<String, Model> models = ModelConverters.getInstance().read(responseClassType);
                if (models.isEmpty()) {
                    Property p = ModelConverters.getInstance().readAsProperty(responseClassType);
                    operation.response(responseCode, new Response()
                            .description("successful operation")
                            .schema(p)
                            .headers(defaultResponseHeaders));
                }
                for (String key : models.keySet()) {
                    Property responseProperty = RESPONSE_CONTAINER_CONVERTER.withResponseContainer(responseContainer, new RefProperty().asDefault(key));


                    operation.response(responseCode, new Response()
                            .description("successful operation")
                            .schema(responseProperty)
                            .headers(defaultResponseHeaders));
                    swagger.model(key, models.get(key));
                }
            }
            Map<String, Model> models = ModelConverters.getInstance().readAll(responseClassType);
            for (Map.Entry<String, Model> entry : models.entrySet()) {
                swagger.model(entry.getKey(), entry.getValue());
            }
        }

        Consumes consumes = AnnotationUtils.findAnnotation(method, Consumes.class);
        if (consumes != null) {
            for (String mediaType : consumes.value()) {
                operation.consumes(mediaType);
            }
        }

        Produces produces = AnnotationUtils.findAnnotation(method, Produces.class);
        if (produces != null) {
            for (String mediaType : produces.value()) {
                operation.produces(mediaType);
            }
        }

        ApiResponses responseAnnotation = AnnotationUtils.findAnnotation(method, ApiResponses.class);
        if (responseAnnotation != null) {
            updateApiResponse(operation, responseAnnotation);
        }

        if (AnnotationUtils.findAnnotation(method, Deprecated.class) != null) {
            operation.deprecated(true);
        }

        // process parameters
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = findParamAnnotations(method);

        // paramTypes = method.getParameterTypes
        // genericParamTypes = method.getGenericParameterTypes
        for (int i = 0; i < parameterTypes.length; i++) {
            Type type = genericParameterTypes[i];
            List<Annotation> annotations = Arrays.asList(paramAnnotations[i]);
            List<Parameter> parameters = getParameters(type, annotations);

            for (Parameter parameter : parameters) {
                operation.parameter(parameter);
            }
        }
        if (operation.getResponses() == null) {
            operation.defaultResponse(new Response().description("successful operation"));
        }

        // Process @ApiImplicitParams
        this.readImplicitParameters(method, operation);

        processOperationDecorator(operation, method);

        return operation;
    }

	private Class<?> convertToClass(Type type) {
		Type typeToConvert = type;
		if (type instanceof ParameterizedType) {
			typeToConvert = ((ParameterizedType) type).getRawType();
		}
		try {
			return Class.forName(getClassName(typeToConvert));
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getClassName(Type type) {
		String fullName = type.toString();
		if (fullName.startsWith(CLASS_NAME_PREFIX)) {
			return fullName.substring(CLASS_NAME_PREFIX.length());
		} else if (fullName.startsWith(INTERFACE_NAME_PREFIX)) {
			return fullName.substring(INTERFACE_NAME_PREFIX.length());
		}
		return fullName;
	}

	private Annotation[][] findParamAnnotations(Method method) {
		Annotation[][] paramAnnotation = method.getParameterAnnotations();

		Method overriddenMethod = ReflectionUtils.getOverriddenMethod(method);
		while(overriddenMethod != null) {
			paramAnnotation = merge(overriddenMethod.getParameterAnnotations(), paramAnnotation);
			overriddenMethod = ReflectionUtils.getOverriddenMethod(overriddenMethod);
		}
		return paramAnnotation;
	}


    private Annotation[][] merge(Annotation[][] overriddenMethodParamAnnotation,
			Annotation[][] currentParamAnnotations) {
    	Annotation[][] mergedAnnotations = new Annotation[overriddenMethodParamAnnotation.length][];

    	for(int i=0; i<overriddenMethodParamAnnotation.length; i++) {
    		mergedAnnotations[i] = merge(overriddenMethodParamAnnotation[i], currentParamAnnotations[i]);
    	}
		return mergedAnnotations;
	}

	private Annotation[] merge(Annotation[] annotations,
			Annotation[] annotations2) {
		List<Annotation> mergedAnnotations = new ArrayList<Annotation>();
		mergedAnnotations.addAll(Arrays.asList(annotations));
		mergedAnnotations.addAll(Arrays.asList(annotations2));
		return mergedAnnotations.toArray(new Annotation[0]);
	}

	public String extractOperationMethod(ApiOperation apiOperation, Method method, Iterator<SwaggerExtension> chain) {
        String apiOperationMethod = swaggerAnnotationHelper.getHttpMethod(apiOperation);
        if (!apiOperationMethod.isEmpty()) {
            return apiOperationMethod.toLowerCase();
        } else if (AnnotationUtils.findAnnotation(method, GET.class) != null) {
            return "get";
        } else if (AnnotationUtils.findAnnotation(method, PUT.class) != null) {
            return "put";
        } else if (AnnotationUtils.findAnnotation(method, POST.class) != null) {
            return "post";
        } else if (AnnotationUtils.findAnnotation(method, DELETE.class) != null) {
            return "delete";
        } else if (AnnotationUtils.findAnnotation(method, OPTIONS.class) != null) {
            return "options";
        } else if (AnnotationUtils.findAnnotation(method, HEAD.class) != null) {
            return "head";
        } else if (AnnotationUtils.findAnnotation(method, io.swagger.jaxrs.PATCH.class) != null) {
            return "patch";
        } else {
            // check for custom HTTP Method annotations
            for (Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
                Annotation[] innerAnnotations = declaredAnnotation.annotationType().getAnnotations();
                for (Annotation innerAnnotation : innerAnnotations) {
                    if (innerAnnotation instanceof HttpMethod) {
                        HttpMethod httpMethod = (HttpMethod) innerAnnotation;
                        return httpMethod.value().toLowerCase();
                    }
                }
            }

            if (chain.hasNext()) {
                return chain.next().extractOperationMethod(apiOperation, method, chain);
            }
        }

        return null;
    }


}
