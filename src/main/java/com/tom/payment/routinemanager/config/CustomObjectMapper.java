package com.tom.payment.routinemanager.config;

import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CustomObjectMapper {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        log.info("Construct customized object mapper");  
        return objectMapper;
    }

}
