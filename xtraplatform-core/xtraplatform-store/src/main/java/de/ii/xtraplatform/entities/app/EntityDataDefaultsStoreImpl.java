package de.ii.xtraplatform.entities.app;

import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.dropwizard.api.Jackson;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityDataBuilder;
import de.ii.xtraplatform.entities.domain.EntityDataDefaultsPath;
import de.ii.xtraplatform.entities.domain.EntityDataDefaultsStore;
import de.ii.xtraplatform.entities.domain.EntityFactory;
import de.ii.xtraplatform.event.store.EventSourcing;
import de.ii.xtraplatform.store.app.ValueDecoderBase;
import de.ii.xtraplatform.store.app.ValueDecoderEnvVarSubstitution;
import de.ii.xtraplatform.store.app.ValueEncodingJackson;
import de.ii.xtraplatform.store.domain.AbstractMergeableKeyValueStore;
import de.ii.xtraplatform.store.domain.EventStore;
import de.ii.xtraplatform.store.domain.Identifier;
import de.ii.xtraplatform.store.domain.ImmutableIdentifier;
import de.ii.xtraplatform.store.domain.ImmutableMutationEvent;
import de.ii.xtraplatform.store.domain.KeyPathAlias;
import de.ii.xtraplatform.store.domain.MergeableKeyValueStore;
import de.ii.xtraplatform.store.domain.MutationEvent;
import de.ii.xtraplatform.store.domain.ValueCache;
import de.ii.xtraplatform.store.domain.ValueEncoding;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component(publicFactory = false)
@Provides
@Instantiate
public class EntityDataDefaultsStoreImpl extends AbstractMergeableKeyValueStore<Map<String,Object>> implements EntityDataDefaultsStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityDataDefaultsStoreImpl.class);

    private final EntityFactory entityFactory;
    private final ValueEncodingJackson<Map<String,Object>> valueEncoding;
    private final ValueEncodingJackson<EntityDataBuilder<EntityData>> valueEncodingBuilder;
    private final EventSourcing<Map<String,Object>> eventSourcing;

    protected EntityDataDefaultsStoreImpl(@Requires EventStore eventStore, @Requires Jackson jackson,
                                          @Requires EntityFactory entityFactory) {
        this.entityFactory = entityFactory;
        this.valueEncoding = new ValueEncodingJackson<>(jackson);
        this.eventSourcing = new EventSourcing<>(eventStore, ImmutableList.of(EntityDataDefaultsStore.EVENT_TYPE), valueEncoding, this::onStart, Optional.of(this::processEvent));

        valueEncoding.addDecoderPreProcessor(new ValueDecoderEnvVarSubstitution());
        valueEncoding.addDecoderMiddleware(new ValueDecoderBase<>(this::getDefaults, eventSourcing));

        this.valueEncodingBuilder = new ValueEncodingJackson<>(jackson);
        valueEncodingBuilder.addDecoderMiddleware(new ValueDecoderBase<>(this::getNewBuilder, new ValueCache<EntityDataBuilder<EntityData>>() {
            @Override
            public boolean isInCache(Identifier identifier) {
                return false;
            }

            @Override
            public EntityDataBuilder<EntityData> getFromCache(Identifier identifier) {
                return null;
            }
        }));
    }

    //TODO: it seems this is needed for correct order (defaults < entities)
    @Validate
    private void onVal() {
        //LOGGER.debug("VALID");
    }

    //TODO: onEmit middleware
    private List<MutationEvent> processEvent(MutationEvent event) {

        if (valueEncoding.isEmpty(event.payload())) {
            return ImmutableList.of();
        }

        EntityDataDefaultsPath defaultsPath = EntityDataDefaultsPath.from(event.identifier());

        List<List<String>> subTypes = entityFactory.getSubTypes(defaultsPath.getEntityType(), defaultsPath.getEntitySubtype());

        //LOGGER.debug("Applying to subtypes as well: {}", subTypes);

        List<Identifier> cacheKeys = getCacheKeys(defaultsPath, subTypes);

        //LOGGER.debug("Applying to subtypes as well 2: {}", cacheKeys);

        return cacheKeys.stream()
                        .map(cacheKey -> {
                            ImmutableMutationEvent.Builder builder = ImmutableMutationEvent.builder()
                                                                                           .from(event)
                                                                                           .identifier(cacheKey);
                            if (!defaultsPath.getKeyPath()
                                             .isEmpty()) {
                                Optional<KeyPathAlias> keyPathAlias = entityFactory.getKeyPathAlias(defaultsPath.getKeyPath()
                                                                                                                .get(defaultsPath.getKeyPath()
                                                                                                                                                      .size() - 1));
                                try {
                                    byte[] nestedPayload = valueEncoding.nestPayload(event.payload(), event.format(), defaultsPath.getKeyPath(), keyPathAlias);
                                    builder.payload(nestedPayload);
                                } catch (IOException e) {
                                    LOGGER.error("Error:", e);
                                }
                            }

                            return builder.build();
                        })
                        .collect(Collectors.toList());
    }

    private List<Identifier> getCacheKeys(EntityDataDefaultsPath defaultsPath, List<List<String>> subTypes) {

        return ImmutableList.<Identifier>builder()
                .add(ImmutableIdentifier.builder()
                                        .addPath(defaultsPath.getEntityType())
                                        .addAllPath(defaultsPath.getEntitySubtype())
                                        .id(EntityDataDefaultsStore.EVENT_TYPE)
                                        .build())
                .addAll(subTypes.stream()
                                .map(subType -> ImmutableIdentifier.builder()
                                                                   .addPath(defaultsPath.getEntityType())
                                                                   .addAllPath(subType)
                                                                   .id(EntityDataDefaultsStore.EVENT_TYPE)
                                                                   .build())
                                .collect(Collectors.toList()))
                .build();
    }

    private Map<String,Object> getDefaults(Identifier identifier) {
        if (eventSourcing.isInCache(identifier)) {
            return eventSourcing.getFromCache(identifier);
        }

        return new LinkedHashMap<>();
    }

    public EntityDataBuilder<EntityData> getNewBuilder(Identifier identifier) {

        EntityDataDefaultsPath defaultsPath = EntityDataDefaultsPath.from(identifier);

        Optional<String> subtype = entityFactory.getTypeAsString(defaultsPath.getEntitySubtype());

        return entityFactory.getDataBuilder(defaultsPath.getEntityType(), subtype);
    }

    @Override
    public EntityDataBuilder<EntityData> getBuilder(Identifier identifier) {

        if (eventSourcing.isInCache(identifier)) {
            Map<String, Object> defaults = eventSourcing.getFromCache(identifier);
            byte[] payload = valueEncodingBuilder.serialize(defaults);

            return valueEncodingBuilder.deserialize(identifier, payload, valueEncodingBuilder.getDefaultFormat());
        }

        return getNewBuilder(identifier);
    }

    @Override
    protected ValueEncoding<Map<String,Object>> getValueEncoding() {
        return valueEncoding;
    }

    @Override
    protected EventSourcing<Map<String,Object>> getEventSourcing() {
        return eventSourcing;
    }

    @Override
    public <U extends Map<String, Object>> MergeableKeyValueStore<U> forType(Class<U> type) {
        return null;
    }

    @Override
    protected CompletableFuture<Void> onStart() {

        identifiers().forEach(identifier -> {
            //EntityDataBuilder<EntityData> builder = get(identifier);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Loaded defaults: {}", identifier);
            }

            /*try {
                builder.build();
            } catch (Throwable e) {
                LOGGER.debug("Error: {}", e.getMessage());
            }*/

        });

        return super.onStart();
    }
}