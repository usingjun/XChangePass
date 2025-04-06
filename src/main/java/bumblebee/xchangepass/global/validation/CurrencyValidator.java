package bumblebee.xchangepass.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, Currency> {

    @Override
    public boolean isValid(Currency currency, ConstraintValidatorContext constraintValidatorContext) {
        if (currency == null) {
            return false;
        }

        try {
            Currency.getInstance(currency.getCurrencyCode());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}