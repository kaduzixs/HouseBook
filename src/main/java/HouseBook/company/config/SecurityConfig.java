
package HouseBook.company.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/user/login", "/user/register")).authorizeHttpRequests(auth -> auth.requestMatchers("/", "/index.html", "/auth.css", "/auth.js", "/favicon.ico").permitAll().requestMatchers("/user/login", "/user/register").permitAll().anyRequest().authenticated());
        return http.build();
    }
}
