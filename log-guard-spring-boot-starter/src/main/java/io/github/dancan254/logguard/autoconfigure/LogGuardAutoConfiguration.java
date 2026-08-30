package io.github.dancan254.logguard.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import ch.qos.logback.classic.LoggerContext;

@AutoConfiguration
@ConditionalOnClass(LoggerContext.class)
@ConditionalOnProperty(prefix = "log-guard", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(LogGuardProperties.class)
public class LogGuardAutoConfiguration {
}
