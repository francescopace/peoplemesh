package org.peoplemesh.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public final class JsonMergePatchUtils {

    private JsonMergePatchUtils() {}

    public static JsonNode apply(JsonNode target, JsonNode patch) {
        if (patch == null || patch.isNull()) {
            return NullNode.getInstance();
        }
        if (!patch.isObject()) {
            return patch.deepCopy();
        }

        ObjectNode targetObject = target != null && target.isObject()
                ? ((ObjectNode) target.deepCopy())
                : JsonNodeFactory.instance.objectNode();

        for (Map.Entry<String, JsonNode> entry : patch.properties()) {
            String fieldName = entry.getKey();
            JsonNode patchValue = entry.getValue();

            if (patchValue == null || patchValue.isNull()) {
                targetObject.remove(fieldName);
            } else {
                JsonNode existingValue = targetObject.get(fieldName);
                targetObject.set(fieldName, apply(existingValue, patchValue));
            }
        }
        return targetObject;
    }
}
