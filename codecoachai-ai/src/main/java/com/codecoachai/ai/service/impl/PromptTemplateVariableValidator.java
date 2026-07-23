package com.codecoachai.ai.service.impl;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class PromptTemplateVariableValidator {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*}}");
    private static final Pattern VARIABLE_NAME_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PromptTemplateVariableValidator() {
    }

    public static void validateDefinition(String content, String variablesDeclaration) {
        Set<String> placeholders = placeholders(content);
        VariableDeclaration declaration = parseDeclaration(variablesDeclaration);
        Set<String> undeclared = difference(placeholders, declaration.declared());
        Set<String> unused = difference(declaration.declared(), placeholders);
        if (!undeclared.isEmpty() || !unused.isEmpty()) {
            throw parameterError("Prompt variables do not match placeholders. undeclared="
                    + undeclared + ", unused=" + unused);
        }
    }

    public static String render(String content, String variablesDeclaration, Map<String, String> variables) {
        VariableDeclaration declaration = parseDeclaration(variablesDeclaration);
        Map<String, String> safeVariables = variables == null ? Collections.emptyMap() : variables;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content == null ? "" : content);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            boolean optional = declaration.optional().contains(name);
            if (!safeVariables.containsKey(name) && !optional) {
                throw parameterError("Missing required prompt variable: " + name);
            }
            String value = safeVariables.get(name);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static VariableDeclaration parseDeclaration(String value) {
        if (!StringUtils.hasText(value)) {
            return new VariableDeclaration(Collections.emptySet(), Collections.emptySet());
        }
        String declaration = value.trim();
        if (declaration.startsWith("[") || declaration.startsWith("{")) {
            return parseJsonDeclaration(declaration);
        }
        Set<String> declared = new LinkedHashSet<>();
        Set<String> optional = new LinkedHashSet<>();
        for (String token : declaration.split("[,\\r\\n]")) {
            addCsvVariable(token, declared, optional);
        }
        return new VariableDeclaration(declared, optional);
    }

    private static VariableDeclaration parseJsonDeclaration(String value) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            Set<String> declared = new LinkedHashSet<>();
            Set<String> optional = new LinkedHashSet<>();
            if (root.isArray()) {
                addArray(root, declared, optional, false);
            } else if (root.isObject()) {
                boolean structured = root.has("required") || root.has("optional") || root.has("variables");
                if (structured) {
                    addNode(root.get("required"), declared, optional, false);
                    addNode(root.get("optional"), declared, optional, true);
                    addNode(root.get("variables"), declared, optional, false);
                } else {
                    addObjectFields(root, declared, optional);
                }
            } else {
                throw parameterError("Prompt variables declaration must be a CSV list, JSON array, or JSON object.");
            }
            return new VariableDeclaration(declared, optional);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw parameterError("Invalid prompt variables declaration.");
        }
    }

    private static void addNode(JsonNode node, Set<String> declared, Set<String> optional, boolean optionalByDefault) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            addArray(node, declared, optional, optionalByDefault);
            return;
        }
        if (node.isObject()) {
            addObjectFields(node, declared, optional);
            return;
        }
        if (node.isTextual()) {
            addVariable(node.asText(), optionalByDefault, declared, optional);
            return;
        }
        throw parameterError("Invalid prompt variable declaration entry.");
    }

    private static void addArray(JsonNode array, Set<String> declared, Set<String> optional,
                                 boolean optionalByDefault) {
        for (JsonNode item : array) {
            if (item.isTextual()) {
                addVariable(item.asText(), optionalByDefault, declared, optional);
            } else if (item.isObject() && item.hasNonNull("name")) {
                boolean isOptional = item.has("required")
                        ? !item.path("required").asBoolean(true)
                        : optionalByDefault;
                addVariable(item.path("name").asText(), isOptional, declared, optional);
            } else {
                throw parameterError("Invalid prompt variable declaration entry.");
            }
        }
    }

    private static void addObjectFields(JsonNode object, Set<String> declared, Set<String> optional) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode metadata = field.getValue();
            boolean isOptional = metadata != null
                    && ((metadata.isBoolean() && !metadata.asBoolean())
                    || (metadata.isObject() && metadata.has("required")
                    && !metadata.path("required").asBoolean(true)));
            addVariable(field.getKey(), isOptional, declared, optional);
        }
    }

    private static void addCsvVariable(String token, Set<String> declared, Set<String> optional) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String name = token.trim();
        boolean isOptional = name.endsWith("?");
        addVariable(isOptional ? name.substring(0, name.length() - 1) : name,
                isOptional, declared, optional);
    }

    private static void addVariable(String value, boolean isOptional, Set<String> declared, Set<String> optional) {
        String name = value == null ? "" : value.trim();
        if (!VARIABLE_NAME_PATTERN.matcher(name).matches()) {
            throw parameterError("Invalid prompt variable name: " + name);
        }
        declared.add(name);
        if (isOptional) {
            optional.add(name);
        }
    }

    private static Set<String> placeholders(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static BusinessException parameterError(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }

    private record VariableDeclaration(Set<String> declared, Set<String> optional) {

        private VariableDeclaration {
            declared = Collections.unmodifiableSet(new LinkedHashSet<>(declared));
            optional = Collections.unmodifiableSet(new LinkedHashSet<>(optional));
        }
    }
}
