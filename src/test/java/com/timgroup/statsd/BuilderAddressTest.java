package com.timgroup.statsd;

import java.net.SocketAddress;
import java.net.InetSocketAddress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import jnr.unixsocket.UnixSocketAddress;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runners.MethodSorters;
import org.junit.function.ThrowingRunnable;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class BuilderAddressTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    final String url;
    final String host;
    final String port;
    final String pipe;
    final SocketAddress expected;

    public BuilderAddressTest(String url, String host, String port, String pipe, SocketAddress expected) {
        this.url = url;
        this.host = host;
        this.port = port;
        this.pipe = pipe;
        this.expected = expected;
    }

    static boolean isJnrAvailable() {
        try {
            Class.forName("jnr.unixsocket.UnixDatagramChannel");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static final private int defaultPort = NonBlockingStatsDClient.DEFAULT_DOGSTATSD_PORT;

    @Parameters
    public static Collection<Object[]> parameters() {
        ArrayList params = new ArrayList();

        params.addAll(Arrays.asList(new Object[][]{
            // DD_DOGSTATSD_URL
            { "udp://1.1.1.1", null, null, null, new InetSocketAddress("1.1.1.1", defaultPort) },
            { "udp://1.1.1.1:9999", null, null, null, new InetSocketAddress("1.1.1.1", 9999) },
            { "\\\\.\\pipe\\foo", null, null, null, new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },

            // DD_AGENT_HOST
            { null, "1.1.1.1", null, null, new InetSocketAddress("1.1.1.1", defaultPort) },

            // DD_AGENT_HOST, DD_DOGSTATSD_PORT
            { null, "1.1.1.1", "9999", null, new InetSocketAddress("1.1.1.1", 9999) },

            { null, null, null, "foo", new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },

            // DD_DOGSTATSD_URL overrides other env vars.
            { "udp://1.1.1.1", null, null, "foo", new InetSocketAddress("1.1.1.1", defaultPort) },
            { "udp://1.1.1.1:9999", null, null, "foo", new InetSocketAddress("1.1.1.1", 9999) },
            { "\\\\.\\pipe\\foo", null, null, "bar", new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },
            { "\\\\.\\pipe\\foo", "1.1.1.1", null, null, new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },
            { "\\\\.\\pipe\\foo", "1.1.1.1", "9999", null, new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },

            // DD_DOGSTATSD_NAMED_PIPE overrides DD_AGENT_HOST.
            { null, "1.1.1.1", null, "foo", new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },
            { null, "1.1.1.1", "9999", "foo", new NamedPipeSocketAddress("\\\\.\\pipe\\foo") },
        }));

        if (isJnrAvailable()) {
            params.addAll(Arrays.asList(new Object[][]{
                { "unix:///dsd.sock", null, null, null, new UnixSocketAddress("/dsd.sock") },
                { "unix://unused/dsd.sock", null, null, null, new UnixSocketAddress("/dsd.sock") },
                { "unix://unused:9999/dsd.sock", null, null, null, new UnixSocketAddress("/dsd.sock") },
                { null, "/dsd.sock", "0", null, new UnixSocketAddress("/dsd.sock") },
                { "unix:///dsd.sock", "1.1.1.1", "9999", null, new UnixSocketAddress("/dsd.sock") },
            }));
        }

        return params;
    }

    @Before
    public void set() {
        set(NonBlockingStatsDClient.DD_DOGSTATSD_URL_ENV_VAR, url);
        set(NonBlockingStatsDClient.DD_AGENT_HOST_ENV_VAR, host);
        set(NonBlockingStatsDClient.DD_DOGSTATSD_PORT_ENV_VAR, port);
        set(NonBlockingStatsDClient.DD_NAMED_PIPE_ENV_VAR, pipe);
    }

    void set(String name, String val) {
        if (val != null) {
            environmentVariables.set(name, val);
        } else {
            environmentVariables.clear(name);
        }
    }

    @Test(timeout = 5000L)
    public void address_resolution() throws Exception {
        NonBlockingStatsDClientBuilder b;

        // Default configuration matches env vars
        b = new NonBlockingStatsDClientBuilder().resolve();
        assertEquals(expected, b.addressLookup.call());

        // Explicit configuration is used regardless of environment variables.
        b = new NonBlockingStatsDClientBuilder().hostname("2.2.2.2").resolve();
        assertEquals(new InetSocketAddress("2.2.2.2", defaultPort), b.addressLookup.call());

        b = new NonBlockingStatsDClientBuilder().hostname("2.2.2.2").port(2222).resolve();
        assertEquals(new InetSocketAddress("2.2.2.2", 2222), b.addressLookup.call());

        b = new NonBlockingStatsDClientBuilder().namedPipe("ook").resolve();
        assertEquals(new NamedPipeSocketAddress("ook"), b.addressLookup.call());
    }
}
