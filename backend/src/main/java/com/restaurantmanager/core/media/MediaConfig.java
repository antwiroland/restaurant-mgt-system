package com.restaurantmanager.core.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(MediaProps.class)
public class MediaConfig implements WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(MediaConfig.class);

    private final MediaProps props;

    public MediaConfig(MediaProps props) {
        this.props = props;
    }

    @Bean
    MediaStorage mediaStorage() {
        if ("cloudinary".equalsIgnoreCase(props.getProvider())) {
            log.info("Storing media on Cloudinary (cloud {})", props.getCloudinary().getCloudName());
            return new CloudinaryMediaStorage(props);
        }
        log.info("Storing media on local disk in {}", props.getLocalDir());
        return new LocalMediaStorage(props);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!"cloudinary".equalsIgnoreCase(props.getProvider())) {
            String location = java.nio.file.Path.of(props.getLocalDir()).toAbsolutePath().normalize().toUri().toString();
            registry.addResourceHandler("/media/**").addResourceLocations(location.endsWith("/") ? location : location + "/");
        }
    }
}
