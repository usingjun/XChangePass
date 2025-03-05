//package bumblebee.xchangepass.domain.ExchangeRate.util;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.persistence.AttributeConverter;
//import jakarta.persistence.Converter;
//import java.io.IOException;
//import java.util.Map;
//
//@Converter(autoApply = true)
//public class JsonbConverter implements AttributeConverter<Map<String, Double>, String> {
//
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    @Override
//    public String convertToDatabaseColumn(Map<String, Double> attribute) {
//        try {
//            // Map을 JSON 문자열로 변환하여 반환
//            return objectMapper.writeValueAsString(attribute);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to convert Map to JSON", e);
//        }
//    }
//
//    @Override
//    public Map<String, Double> convertToEntityAttribute(String dbData) {
//        try {
//            // DB에서 가져온 JSON 문자열을 Map으로 변환
//            return objectMapper.readValue(dbData, Map.class);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to convert JSON to Map", e);
//        }
//    }
//}
