package com.histudio.web.netbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
public class SecurityConfig {

	@Bean
	UserDetailsService noPasswordUsers() {
		return username -> {
			throw new UsernameNotFoundException("Password authentication is not supported.");
		};
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, LoopbackOnlyFilter loopbackOnlyFilter) throws Exception {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");

		XorCsrfTokenRequestAttributeHandler requestHandler = new XorCsrfTokenRequestAttributeHandler();
		requestHandler.setCsrfRequestAttributeName("_csrf");

		http
			.addFilterBefore(loopbackOnlyFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
			.csrf(csrf -> csrf
				.ignoringRequestMatchers("/api/v1/mobile/**")
				.csrfTokenRepository(csrfRepository)
				.csrfTokenRequestHandler(requestHandler))
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives(
					"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
						+ "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; "
						+ "form-action 'self'; frame-ancestors 'none'"))
				.frameOptions(frame -> frame.deny())
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
				.addHeaderWriter(new StaticHeadersWriter(
					"Permissions-Policy",
					"camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
				.httpStrictTransportSecurity(Customizer.withDefaults()));

		return http.build();
	}
}
