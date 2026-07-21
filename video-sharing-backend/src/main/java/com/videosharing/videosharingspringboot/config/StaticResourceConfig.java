package com.videosharing.videosharingspringboot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.thumbnail-dir}")
    private String thumbnailDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(toFileResource(uploadDir));
        registry.addResourceHandler("/thumbnails/**")
                .addResourceLocations(toFileResource(thumbnailDir));
    }

    private String toFileResource(String directory) {
        return Paths.get(directory).toAbsolutePath().normalize().toUri().toString();
    }
}
