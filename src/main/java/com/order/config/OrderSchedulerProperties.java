package com.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "order.scheduler")
@Validated
@Getter
@Setter
public class OrderSchedulerProperties {

    @NotNull
    @Min(1000)
    private Long intervalMs = 60000L;

    @NotNull
    @Min(1)
    private Long advanceThresholdMinutes = 1L;
}
