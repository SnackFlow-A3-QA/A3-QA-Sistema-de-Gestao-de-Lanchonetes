package br.com.snackflow.cliente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link TelefoneValido}: telefone inválido vira 400 com a mensagem no campo. */
public class TelefoneValidador implements ConstraintValidator<TelefoneValido, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null || valor.isBlank()) {
            return true;
        }
        return Telefone.formatar(valor).isPresent();
    }
}
