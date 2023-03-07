package top.iot.gateway.supports.official;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.dict.EnumDict;
import top.iot.gateway.core.metadata.DataType;
import top.iot.gateway.core.metadata.types.*;
import org.junit.Test;
import org.springframework.core.ResolvableType;

import static org.junit.Assert.assertTrue;

public class MetadataParserTest {


    @Test
    public void testParse() {

        DataType type = DeviceMetadataParser.withType(ResolvableType.forType(TestClazz.class));
        assertTrue(type instanceof ObjectType);

        ObjectType objectType = ((ObjectType) type);

        assertTrue(
                objectType.getProperty("idx").get().getValueType() instanceof IntType
        );

        assertTrue(
                objectType.getProperty("obj").get().getValueType() instanceof ObjectType
        );

        assertTrue(
                objectType.getProperty("id").get().getValueType() instanceof StringType
        );

        assertTrue(
                objectType.getProperty("enums").get().getValueType() instanceof EnumType
        );

        assertTrue(
                objectType.getProperty("dict").get().getValueType() instanceof EnumType
        );

        assertTrue(
                objectType.getProperty("dicts").get().getValueType() instanceof ArrayType
        );

    }

    @Getter
    @Setter
    static class TestClazz extends Generic<String> {
        @Schema(description = "index")
        private int idx;

        @Schema(description = "obj")
        private Entity obj;

        @Schema(description = "enums")
        private SimpleEnum enums;

        @Schema(description = "dict")
        private DicEnum dict;

        @Schema(description = "dicts")
        private DicEnum[] dicts;
    }

    @Getter
    @Setter
    static class Entity {
        @Schema(description = "name")
        private String name;

    }

    @Getter
    @Setter
    static class Generic<T>{

        @Schema(description = "id")
        private T id;
    }
    enum DicEnum implements EnumDict<String> {
        a,b,v;

        @Override
        public String getValue() {
            return name();
        }

        @Override
        public String getText() {
            return name().toUpperCase();
        }
    }
    enum SimpleEnum{
        a,b,v
    }
}