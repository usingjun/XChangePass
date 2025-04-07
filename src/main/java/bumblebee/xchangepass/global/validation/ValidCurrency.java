package bumblebee.xchangepass.global.validation;

import jakarta.validation.Constraint;
import org.springframework.messaging.handler.annotation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CurrencyValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrency {
    String message() default "유효하지 않은 통화 코드입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}