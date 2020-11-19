package dev.psmolinski.ogg;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.transforms.Transformation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TransformerConverter implements Converter  {

    private SchemaRegistryClient registry;

    private Converter converter;

    private List<Transformation<SourceRecord>> transformations;

    /**
     * Default constructor used by GoldenGate
     */
    public TransformerConverter() {
    }

    /**
     * Schema registry client injection.
     * @param registry
     */
    public TransformerConverter(SchemaRegistryClient registry) {
        this.registry = registry;
    }


    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

        TransformerConverterConfig config = new TransformerConverterConfig((Map<String,String>)configs, isKey);

        this.converter = config.converter(registry);

        this.transformations = config.transformations();

    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object value) {

        SourceRecord record = transformations.stream().reduce(
                new SourceRecord(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        topic,
                        schema,
                        value
                ),
                (r,t) -> t.apply(r),
                (a,b) -> a
        );

        if (record == null) {
            return null;
        }

        return converter.fromConnectData(
                record.topic(),
                record.valueSchema(),
                record.value());

    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] value) {
        throw new UnsupportedOperationException("Operation toConnectData is unsupported");
    }

}
