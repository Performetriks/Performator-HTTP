package com.performetriks.performator.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

import com.xresch.hsr.base.HSR;

/***************************************************************************
 * 
 * Decorator for ConnectionSocketFactory to measure connect time.
 * 
 * @author Perfluencer
 * 
 ***************************************************************************/
public class PFRSocketFactory implements ConnectionSocketFactory {

	protected final ConnectionSocketFactory delegate;

	public PFRSocketFactory(ConnectionSocketFactory delegate) {
		this.delegate = delegate;
	}

	@Override
	public Socket createSocket(HttpContext context) throws IOException {
		return delegate.createSocket(context);
	}

	@Override
	public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
			InetSocketAddress localAddress, HttpContext context) throws IOException {

		String metric = PFRHttp.currentMetricName();

		if (metric != null) {
			HSR.start(metric + "-Connect");
			try {
				return delegate.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
			} finally {
				HSR.end();
			}
		} else {
			return delegate.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
		}
	}

	public static class PFRLayeredSocketFactory extends PFRSocketFactory implements LayeredConnectionSocketFactory {

		public PFRLayeredSocketFactory(LayeredConnectionSocketFactory delegate) {
			super(delegate);
		}

		@Override
		public Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context)
				throws IOException {

			String metric = PFRHttp.currentMetricName();
			if (metric != null) {
				HSR.start(metric + "-TLS");
				try {
					return ((LayeredConnectionSocketFactory) delegate).createLayeredSocket(socket, target, port,
							context);
				} finally {
					HSR.end();
				}
			} else {
				return ((LayeredConnectionSocketFactory) delegate).createLayeredSocket(socket, target, port, context);
			}
		}

		@Override
		public Socket createLayeredSocket(Socket socket, String target, int port, Object attachment,
				HttpContext context)
				throws IOException {
			String metric = PFRHttp.currentMetricName();
			if (metric != null) {
				HSR.start(metric + "-TLS");
				try {
					return ((LayeredConnectionSocketFactory) delegate).createLayeredSocket(socket, target, port,
							attachment, context);
				} finally {
					HSR.end();
				}
			} else {
				return ((LayeredConnectionSocketFactory) delegate).createLayeredSocket(socket, target, port, attachment,
						context);
			}
		}

	}
}
