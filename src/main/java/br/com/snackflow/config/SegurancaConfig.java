package br.com.snackflow.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança compartilhada: login por formulário (funcionarios.login + BCrypt), CSRF ligado,
 * logout por POST e acesso por perfil conforme {@link Tela}. Quem não está autenticado vai
 * para /login; autenticado em rota de outro perfil recebe 403.
 *
 * <p>Arquivo compartilhado: não altere sem alinhar com o responsável pelo pacote config.
 */
@Configuration
@EnableWebSecurity
class SegurancaConfig {

    private static final String LOGIN = "/login";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filtroDeSeguranca(HttpSecurity http, RedirecionamentoPorPerfilHandler sucesso)
            throws Exception {
        http.authorizeHttpRequests(acesso -> {
            acesso.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            acesso.requestMatchers(LOGIN, "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll();
            for (Tela tela : Tela.values()) {
                acesso.requestMatchers(tela.rota(), tela.rota() + "/**").hasAnyRole(tela.papeis());
            }
            acesso.anyRequest().authenticated();
        });
        http.formLogin(form -> form
                .loginPage(LOGIN)
                .loginProcessingUrl(LOGIN)
                .usernameParameter("login")
                .passwordParameter("senha")
                .successHandler(sucesso)
                .failureUrl("/login?erro")
                .permitAll());
        http.logout(saida -> saida
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?saiu")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
