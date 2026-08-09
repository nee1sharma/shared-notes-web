package com.histudio.web.sharednotebook.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoopbackOnlyFilter extends OncePerRequestFilter {

	private final boolean loopbackOnly;

	public LoopbackOnlyFilter(
			@Value("${shared-notebook.web.root-admin-loopback-only:true}") boolean loopbackOnly) {
		this.loopbackOnly = loopbackOnly;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (loopbackOnly && !isLoopback(request.getRemoteAddr())) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"code\":\"loopback_required\",\"message\":\"SharedNoteBook web access is limited to the host device.\"}");
			return;
		}

		if (request.getRequestURI().startsWith("/api/")) {
			response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
			response.setHeader("Pragma", "no-cache");
		}
		filterChain.doFilter(request, response);
	}

	private boolean isLoopback(String address) {
		try {
			return InetAddress.getByName(address).isLoopbackAddress();
		} catch (UnknownHostException exception) {
			return false;
		}
	}
}
