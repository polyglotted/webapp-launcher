package org.polyglotted.webapp.launcher;

import com.typesafe.config.Config;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.polyglotted.webapp.core.Gaveti;
import org.polyglotted.webapp.core.Overrides;
import org.polyglotted.webapp.resources.impl.DefaultResources;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static com.google.common.collect.Sets.newHashSet;
import static org.polyglotted.webapp.resources.impl.DefaultResources.ResourceNotFoundException;

@Slf4j
@RequiredArgsConstructor
public class SpringServer {
    private static final String Configuration_Class = "app.spring.MainConfiguration";
    private static final String Gaveti_Bean = "gaveti";
    private static final String Overrides_Bean = "overrides";

    private final Gaveti gaveti;
    private final Overrides overrides;
    private final Class<?>[] configurationClasses;

    @Getter
    private AnnotationConfigApplicationContext applicationContext;
    private final CountDownLatch latch = new CountDownLatch(1);

    public void start() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getEnvironment().setActiveProfiles(profiles());
        applicationContext.register(configurationClasses);
        overrideBeans();
        applicationContext.refresh();
        applicationContext.start();
        new Thread(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                latch.await();
            }
        }).start();
    }

    public void stop() {
        applicationContext.close();
        latch.countDown();
    }

    private String[] profiles() {
        Set<String> activeProfiles = newHashSet(applicationContext.getEnvironment().getActiveProfiles());
        activeProfiles.add(gaveti.environment());
        if (gaveti.instanceName().isSome()) {
            activeProfiles.add(gaveti.instanceName().some());
        }
        try {
            Config config = new DefaultResources(gaveti).config("spring.properties");
            if (config.hasPath("spring.profiles.active")) {
                activeProfiles.addAll(Arrays.asList(config.getString("spring.profiles.active").trim().split(",")));
            }
        } catch (ResourceNotFoundException e) {
            log.info("spring.properties not found, no additional spring properties loaded");
        }
        log.info("Setting spring profiles {}", activeProfiles);
        return activeProfiles.toArray(new String[0]);
    }

    private void overrideBeans() {
        final ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        if (applicationContext.containsBean(Gaveti_Bean)) {
            applicationContext.removeBeanDefinition(Gaveti_Bean);
        }
        beanFactory.registerSingleton(Gaveti_Bean, gaveti);
        beanFactory.registerSingleton(Overrides_Bean, overrides);
    }

    @SneakyThrows
    static Class<?>[] defaultConfigurationClasses() {
        return new Class[]{Class.forName(Configuration_Class)};
    }
}
