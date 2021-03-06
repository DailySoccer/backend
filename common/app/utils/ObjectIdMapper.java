package utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bson.types.ObjectId;
import org.joda.money.Money;

import java.io.IOException;

// Usamos este mapper solo durante la serializacion para el cliente, para que siempre le lleguen los ObjectId como cadenas simples
public class ObjectIdMapper extends ObjectMapper {

    public ObjectIdMapper() {
        SimpleModule module = new SimpleModule("ObjectIdModule");
        module.addSerializer(ObjectId.class, new ObjectIdSerializer());
        module.addSerializer(Money.class, new JacksonJodaMoney.MoneySerializer());
        module.addDeserializer(Money.class, new JacksonJodaMoney.MoneyDeserializer());
        this.registerModule(module);

        setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        setVisibility(PropertyAccessor.FIELD, Visibility.PUBLIC_ONLY);
        setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static class ObjectIdSerializer extends JsonSerializer<ObjectId> {
        @Override
        public void serialize(ObjectId value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeString(value.toString());
        }
    }
}