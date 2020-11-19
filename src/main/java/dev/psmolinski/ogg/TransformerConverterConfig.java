package dev.psmolinski.ogg;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.transforms.Transformation;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class TransformerConverterConfig extends AbstractConfig {

    public static final String TRANSFORMS_CONFIG = "transforms";
    public static final String TRANSFORMS_GROUP = "transforms";

    private boolean isKey;
    public TransformerConverterConfig(Map<String,String> props, boolean isKey) {
        super(withTransforms(CONFIG_DEF, props), props);
        this.isKey = isKey;
    }

    public List<Transformation<SourceRecord>> transformations() {
        final List<String> transformAliases = getList("transforms");
        final List<Transformation<SourceRecord>> transformations = new ArrayList<>(transformAliases.size());
        for (String alias : transformAliases) {
            final String prefix = "transforms" + "." + alias + ".";
            try {
                @SuppressWarnings("unchecked")
                final Transformation<SourceRecord> transformation = getClass(prefix + "type").asSubclass(Transformation.class)
                        .getDeclaredConstructor().newInstance();
                transformation.configure(originalsWithPrefix(prefix));
                transformations.add(transformation);
            } catch (Exception e) {
                throw new ConnectException(e);
            }
        }
        return transformations;
    }

    public Converter converter(SchemaRegistryClient registry) {
        Converter converter;
        Class<Converter> converterClass = (Class<Converter>) getClass("converter");
        try {
            // dirty solution to inject the mock schema registry client
            try {
                converter = converterClass.getDeclaredConstructor(SchemaRegistryClient.class).newInstance(registry);
            } catch (NoSuchMethodException e) {
                converter = converterClass.newInstance();
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed instantiating the converter class", e);
        }
        // needed because Converter does not implement Configurable
        converter.configure(originalsWithPrefix("converter."), isKey);
        return converter;
    }

    private static ConfigDef withTransforms(ConfigDef baseConfigDef, Map<String, String> props) {
        Object transformAliases = ConfigDef.parseType("transforms", props.get("transforms"), ConfigDef.Type.LIST);
        if (!(transformAliases instanceof List)) {
            return baseConfigDef;
        }

        ConfigDef newDef = new ConfigDef(baseConfigDef);
        LinkedHashSet<?> uniqueTransformAliases = new LinkedHashSet<>((List<?>) transformAliases);

        for (Object o : uniqueTransformAliases) {
            if (!(o instanceof String)) {
                throw new ConfigException("Item in " + TRANSFORMS_CONFIG + " property is not of "
                        + "type String");
            }
            String alias = (String) o;
            final String prefix = TRANSFORMS_CONFIG + "." + alias + ".";
            final String group = TRANSFORMS_GROUP + ": " + alias;
            int orderInGroup = 0;

            final String transformationTypeConfig = prefix + "type";
            final ConfigDef.Validator typeValidator = new ConfigDef.Validator() {
                @Override
                public void ensureValid(String name, Object value) {
                    getConfigDefFromTransformation(transformationTypeConfig, (Class) value);
                }
            };
            newDef.define(transformationTypeConfig, ConfigDef.Type.CLASS, ConfigDef.NO_DEFAULT_VALUE, typeValidator, ConfigDef.Importance.HIGH,
                    "Class for the '" + alias + "' transformation.", group, orderInGroup++,
                    ConfigDef.Width.LONG, "Transformation type for " + alias,
                    Collections.<String>emptyList());

            final ConfigDef transformationConfigDef;
            final String className = props.get(transformationTypeConfig);
            final Class<?> cls = (Class<?>) ConfigDef.parseType(transformationTypeConfig, className, ConfigDef.Type.CLASS);
            transformationConfigDef = getConfigDefFromTransformation(transformationTypeConfig, cls);

            newDef.embed(prefix, group, orderInGroup, transformationConfigDef);
        }

        return newDef;

    }

    static ConfigDef getConfigDefFromTransformation(String key, Class<?> transformationCls) {
        if (transformationCls == null || !Transformation.class.isAssignableFrom(transformationCls)) {
            throw new ConfigException(key, String.valueOf(transformationCls), "Not a Transformation");
        }
        Transformation transformation;
        try {
            transformation = transformationCls.asSubclass(Transformation.class).newInstance();
        } catch (Exception e) {
            throw new ConfigException(key, String.valueOf(transformationCls), "Error getting config definition from Transformation: " + e.getMessage());
        }
        ConfigDef configDef = transformation.config();
        if (null == configDef) {
            throw new ConnectException(
                    String.format(
                            "%s.config() must return a ConfigDef that is not null.",
                            transformationCls.getName()
                    )
            );
        }
        return configDef;
    }

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(
                    "converter",
                    ConfigDef.Type.CLASS,
                    ConfigDef.Importance.HIGH,
                    "Underlying converter")
            .define(
                    "transforms",
                    ConfigDef.Type.LIST,
                    ConfigDef.Importance.MEDIUM,
                    "List of transformations")
            ;

    private static final ConfigDef TRANSFORMS_CONFIG_DEF = new ConfigDef()
            .define(
                    "transforms",
                    ConfigDef.Type.LIST,
                    ConfigDef.Importance.MEDIUM,
                    "List of transformations")
            ;

}
