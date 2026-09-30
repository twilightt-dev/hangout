package com.dianping.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Data
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private Path directory;

    public void setDirectory(Path directory) { this.directory = directory; }
}
