package top.iot.gateway.supports.protocol.management.jar;

import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import top.iot.gateway.core.ProtocolSupport;
import top.iot.gateway.core.spi.ProtocolSupportProvider;
import top.iot.gateway.core.spi.ServiceContext;
import top.iot.gateway.core.trace.MonoTracer;
import top.iot.gateway.core.trace.ProtocolTracer;
import top.iot.gateway.core.utils.ClassUtils;
import top.iot.gateway.supports.protocol.management.ProtocolSupportDefinition;
import top.iot.gateway.supports.protocol.management.ProtocolSupportLoaderProvider;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class JarProtocolSupportLoader implements ProtocolSupportLoaderProvider {

    @Setter
    private ServiceContext serviceContext;

    private final Map<String, ProtocolClassLoader> protocolLoaders = new ConcurrentHashMap<>();

    private final Map<String, ProtocolSupportProvider> loaded = new ConcurrentHashMap<>();

    @Override
    public String getProvider() {
        return "jar";
    }

    protected ProtocolClassLoader createClassLoader(URL location) {
        return new ProtocolClassLoader(new URL[]{location}, this.getClass().getClassLoader());
    }

    protected void closeAll() {
        protocolLoaders.values().forEach(this::closeLoader);
        protocolLoaders.clear();
        loaded.clear();
    }

    @SneakyThrows
    protected void closeLoader(ProtocolClassLoader loader) {
        loader.close();
    }

    @Override
    @SneakyThrows
    public Mono<? extends ProtocolSupport> load(ProtocolSupportDefinition definition) {
        String id = definition.getId();
        return Mono
                .defer(() -> {
                    try {

                        Map<String, Object> config = definition.getConfiguration();
                        String location = Optional
                                .ofNullable(config.get("location"))
                                .map(String::valueOf)
                                .orElseThrow(() -> new IllegalArgumentException("location"));

                        URL url;

                        if (!location.contains("://")) {
                            url = new File(location).toURI().toURL();
                        } else {
                            url = new URL("jar:" + location + "!/");
                        }

                        ProtocolClassLoader loader;
                        URL fLocation = url;
                        {
                            ProtocolSupportProvider oldProvider = loaded.remove(id);
                            if (null != oldProvider) {
                                oldProvider.dispose();
                            }
                        }
                        loader = protocolLoaders.compute(id, (key, old) -> {
                            if (null != old) {
                                try {
                                    closeLoader(old);
                                } catch (Exception ignore) {
                                }
                            }
                            return createClassLoader(fLocation);
                        });

                        ProtocolSupportProvider supportProvider;
                        log.debug("load protocol support from : {}", location);
                        String provider = Optional
                                .ofNullable(config.get("provider"))
                                .map(String::valueOf)
                                .map(String::trim)
                                .orElse(null);
                        if (provider != null) {
                            //直接从classLoad获取,防止冲突
                            @SuppressWarnings("all")
                            Class<ProtocolSupportProvider> providerType = (Class) loader.loadSelfClass(provider);
                            supportProvider = providerType.getDeclaredConstructor().newInstance();
                        } else {
                            supportProvider = lookupProvider(loader);
                            if (null == supportProvider) {
                                return Mono.error(new IllegalArgumentException("error.protocol_provider_not_found"));
                            }
                        }
                        ProtocolSupportProvider oldProvider = loaded.put(id, supportProvider);
                        try {
                            if (null != oldProvider) {
                                oldProvider.dispose();
                            }
                        } catch (Throwable e) {
                            log.error(e.getMessage(), e);
                        }
                        return supportProvider
                                .create(serviceContext)
                                .onErrorMap(Exceptions::bubble);
                    } catch (Throwable e) {
                        return Mono.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .as(MonoTracer.create(ProtocolTracer.SpanName.install(id)));
    }

    protected ProtocolSupportProvider lookupProvider(ProtocolClassLoader classLoader) {

        return ClassUtils
                .findImplClass(ProtocolSupportProvider.class,
                               "classpath:**/*.class",
                               true,
                               classLoader,
                               ProtocolClassLoader::loadSelfClass)
                .orElse(null);
    }
}
