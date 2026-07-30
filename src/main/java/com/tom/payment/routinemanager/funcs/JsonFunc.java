package com.tom.payment.routinemanager.funcs;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JsonFunc {

    private final ObjectMapper objectMapper; // Inject the customized object mapper

    public String parseToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }
}
