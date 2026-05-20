package com.codecoachai.common.web.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import java.io.IOException;

/**
 * Jackson 脱敏序列化器。配合 @Sensitive 注解使用。
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private SensitiveType type;

    public SensitiveSerializer() {
    }

    public SensitiveSerializer(SensitiveType type) {
        this.type = type;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(desensitize(value, type));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(Sensitive.class);
        }
        if (annotation != null) {
            return new SensitiveSerializer(annotation.type());
        }
        return this;
    }

    private String desensitize(String value, SensitiveType type) {
        if (type == null || value.isEmpty()) {
            return value;
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> maskIdCard(value);
            case NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case BANK_CARD -> maskBankCard(value);
            case CUSTOM -> "***";
        };
    }

    private String maskPhone(String phone) {
        if (phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskIdCard(String id) {
        if (id.length() < 8) return "****";
        return id.substring(0, 3) + "***********" + id.substring(id.length() - 4);
    }

    private String maskName(String name) {
        if (name.length() <= 1) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    private String maskAddress(String address) {
        if (address.length() <= 6) return "***";
        return address.substring(0, 6) + "***";
    }

    private String maskBankCard(String card) {
        if (card.length() < 8) return "****";
        return card.substring(0, 4) + " **** **** " + card.substring(card.length() - 4);
    }
}
