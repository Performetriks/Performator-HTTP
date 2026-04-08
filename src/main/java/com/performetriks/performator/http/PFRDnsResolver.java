package com.performetriks.performator.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import com.xresch.hsr.base.HSR;

/***************************************************************************
 * 
 * Decorator for DnsResolver to measure resolution time.
 * 
 * @author Perfluencer
 * 
 ***************************************************************************/
public class PFRDnsResolver implements DnsResolver {

	private final DnsResolver delegate;

	public PFRDnsResolver() {
		this(SystemDefaultDnsResolver.INSTANCE);
	}

	public PFRDnsResolver(DnsResolver delegate) {
		this.delegate = delegate;
	}

	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {

		// Map the domain to actual Node IPs to bypass flapping LoadBalancer VIP
		if ("jpetstore.perfluencer.pl".equalsIgnoreCase(host)) {
			return new InetAddress[] {
				InetAddress.getByAddress(new byte[] {(byte)192, (byte)168, (byte)1, (byte)122}),
				InetAddress.getByAddress(new byte[] {(byte)192, (byte)168, (byte)1, (byte)126}),
				InetAddress.getByAddress(new byte[] {(byte)192, (byte)168, (byte)1, (byte)130})
			};
		}

		String metric = PFRHttp.currentMetricName();

		if (metric != null) {
			HSR.start(metric + "-DNS");
			try {
				return delegate.resolve(host);
			} finally {
				HSR.end();
			}
		} else {
			return delegate.resolve(host);
		}
	}

	@Override
	public String resolveCanonicalHostname(String host) throws UnknownHostException {
		return delegate.resolveCanonicalHostname(host);
	}
}
