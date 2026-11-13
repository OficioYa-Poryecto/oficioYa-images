package eci.edu.dosw.image.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    ImageJwtAuthFilter jwtFilter,
                                    RestAuthenticationEntryPoint entryPoint,
                                    RestAccessDeniedHandler deniedHandler) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(a -> a
                        // Requerimiento 1: descarga publica o inter-servicio por ID directo
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/{id}/metadata").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Subidas, consultas por rootId y borrado requieren autenticacion
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/by-root/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/images/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/images/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
