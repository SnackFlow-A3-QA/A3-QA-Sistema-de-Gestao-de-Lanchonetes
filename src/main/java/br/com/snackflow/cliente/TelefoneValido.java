package br.com.snackflow.cliente;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Telefone brasileiro válido (vazio é aceito aqui: a obrigatoriedade é do {@code @NotBlank}). */
@Documented
@Constraint(validatedBy = TelefoneValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefoneValido {

    String message() default "Telefone inválido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
