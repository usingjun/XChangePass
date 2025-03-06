package bumblebee.xchangepass.global.util;

import bumblebee.xchangepass.global.error.ErrorCode;
import com.querydsl.core.types.Path;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.Embeddable;

import java.lang.reflect.Field;
import java.util.Objects;

public class EntityUpdateUtil {

    /**
     * 변경된 필드만 동적으로 업데이트 (Embedded 필드 지원)
     *
     * @param entity        기존 엔티티 (DB에서 조회된 객체)
     * @param updateRequest 업데이트 요청 DTO
     * @param updateClause  QueryDSL 업데이트 클로즈
     * @param qEntityPath       QueryDSL Q-Entity 객체 (예: QUser.user)
     */
    public static <T>  void executeUpdate(
            Object entity,
            Object updateRequest,
            JPAUpdateClause updateClause,
            Path<T> qEntityPath
    ) {
        try {
            boolean isUpdated = false;

            for (Field requestField : updateRequest.getClass().getDeclaredFields()) {
                requestField.setAccessible(true);
                Object newValue = requestField.get(updateRequest);
                if (newValue == null) continue;

                String fieldName = requestField.getName();

                // 1. 엔티티에서 기존 값 가져오기
                Field entityField = entity.getClass().getDeclaredField(fieldName);
                entityField.setAccessible(true);

                // 임베디드 타입인지 확인하고, 임베디드 타입이라면 'value' 필드를 통해 값 가져오기
                Object existingValue;
                boolean isEmbedded = isEmbeddedType(entityField);
                if (isEmbedded) {
                    Field innerValueField = entityField.getType().getDeclaredField("value");
                    innerValueField.setAccessible(true);
                    existingValue = innerValueField.get(entityField.get(entity));
                } else {
                    existingValue = entityField.get(entity);
                }

                // 2. Q-Entity에서 Path 추출
                Field qField = qEntityPath.getClass().getField(fieldName);
                Path<?> fieldPath = (Path<?>) qField.get(qEntityPath);

                // 3. 값 비교 후 업데이트 (임베디드 타입 처리)
                if (!Objects.equals(existingValue, newValue)) {
                    if (isEmbedded) {
                        Field innerValueField = fieldPath.getClass().getDeclaredField("value");
                        innerValueField.setAccessible(true);
                        Path<?> innerFieldPath = (Path<?>) innerValueField.get(fieldPath);
                        updateClause.set((Path<Object>) innerFieldPath, newValue);
                    } else {
                        updateClause.set((Path<Object>) fieldPath, newValue);
                    }
                    isUpdated = true;
                }
            }
            if (isUpdated) {
                updateClause.execute();
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw ErrorCode.ENTITY_FIELD_ACCESS_ERROR.commonException();
        }
    }

    /**
     * 엔티티 필드가 임베디드 타입인지를 확인하는 메서드
     */
    private static boolean isEmbeddedType(Field field) {
        return field.getType().isAnnotationPresent(Embeddable.class);
    }
}
