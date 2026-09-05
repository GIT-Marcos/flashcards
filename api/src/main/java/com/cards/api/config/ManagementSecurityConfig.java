package com.cards.api.config;

import com.cards.api.config.properties.ManagementMetricsProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
@EnableWebSecurity
public class ManagementSecurityConfig {

    private final PasswordEncoder passwordEncoder;
    private final ManagementMetricsProperties properties;

    public ManagementSecurityConfig(PasswordEncoder passwordEncoder, ManagementMetricsProperties properties) {
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Bean
    @Order(0)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(req -> req.getLocalPort() == 9090)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("METRICS")
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public UserDetailsService managementUserDetailsService() {
        UserDetails metricsUser = User.builder()
            .username(properties.getUsername())
            .password(passwordEncoder.encode(properties.getPassword()))
            .roles("METRICS")
            .build();

        return new InMemoryUserDetailsManager(metricsUser);
    }
}
