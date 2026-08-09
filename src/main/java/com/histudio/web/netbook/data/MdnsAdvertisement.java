package com.histudio.web.netbook.data;

import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Advertises the authenticated Android control-plane endpoint on the local network. */
@Component
public final class MdnsAdvertisement {

	private static final String SERVICE_TYPE = "_netbook._tcp.local.";

	private final ControlPlaneService controlPlane;
	private final boolean enabled;
	private final int port;
	private JmDNS jmdns;

	public MdnsAdvertisement(
			ControlPlaneService controlPlane,
			@Value("${netbook.discovery.enabled:true}") boolean enabled,
			@Value("${netbook.discovery.port:8080}") int port) {
		this.controlPlane = controlPlane;
		this.enabled = enabled;
		this.port = port;
	}

	@PostConstruct
	void advertise() {
		if (!enabled) return;
		try {
			jmdns = JmDNS.create();
			String household = controlPlane.householdId().toString().substring(0, 8).toUpperCase();
			ServiceInfo service = ServiceInfo.create(
					SERVICE_TYPE,
					"NetBook-" + household,
					port,
					0,
					0,
					Map.of("protocol", "netbook-mobile-v1", "household", household));
			jmdns.registerService(service);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to advertise the NetBook LAN service.", exception);
		}
	}

	@PreDestroy
	void stopAdvertising() {
		if (jmdns == null) return;
		try {
			jmdns.unregisterAllServices();
			jmdns.close();
		} catch (Exception ignored) {
			// Shutdown must continue even when multicast networking has already stopped.
		}
	}
}
