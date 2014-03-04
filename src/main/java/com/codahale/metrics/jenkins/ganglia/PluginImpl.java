package com.codahale.metrics.jenkins.ganglia;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ganglia.GangliaReporter;
import com.codahale.metrics.jenkins.Metrics;
import hudson.Plugin;
import info.ganglia.gmetric4j.gmetric.GMetric;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public class PluginImpl extends Plugin {
    private static final Logger LOGGER = Logger.getLogger(PluginImpl.class.getName());

    private transient Map<GangliaServer, GangliaReporter> reporters;

    public PluginImpl() {
        this.reporters = new LinkedHashMap<GangliaServer, GangliaReporter>();
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public synchronized void stop() throws Exception {
        if (reporters != null) {
            for (GangliaReporter r : reporters.values()) {
                r.stop();
            }
            reporters.clear();
        }
    }

    @Override
    public synchronized void postInitialize() throws Exception {
        updateReporters();
    }

    public synchronized void updateReporters() throws URISyntaxException, IOException {
        if (reporters == null) {
            reporters = new LinkedHashMap<GangliaServer, GangliaReporter>();
        }
        MetricRegistry registry = Metrics.metricRegistry();
        GangliaServer.DescriptorImpl descriptor =
                Jenkins.getInstance().getDescriptorByType(GangliaServer.DescriptorImpl.class);
        if (descriptor == null) {
            return;
        }
        String url = JenkinsLocationConfiguration.get().getUrl();
        URI uri = url == null ? null : new URI(url);
        String hostname = uri == null ? "localhost" : uri.getHost();
        Set<GangliaServer> toStop = new HashSet<GangliaServer>(reporters.keySet());
        for (GangliaServer s : descriptor.getServers()) {
            toStop.remove(s);
            if (reporters.containsKey(s)) continue;
            GMetric g = new GMetric(s.getHostname(), s.getPort(), GMetric.UDPAddressingMode.MULTICAST, 1);
            String prefix = StringUtils.isBlank(s.getPrefix()) ? hostname : s.getPrefix();
            GangliaReporter r = GangliaReporter.forRegistry(registry)
                    .prefixedWith(prefix)
                    .convertRatesTo(TimeUnit.MINUTES)
                    .convertDurationsTo(TimeUnit.SECONDS)
                    .filter(MetricFilter.ALL)
                    .build(g);
            reporters.put(s, r);
            LOGGER.log(Level.INFO, "Starting Ganglia reporter to {0}:{1} with prefix {2}", new Object[]{
                    s.getHostname(), s.getPort(), prefix
            });
            r.start(1, TimeUnit.MINUTES);
        }
        for (GangliaServer s: toStop) {
            GangliaReporter r = reporters.get(s);
            reporters.remove(s);
            r.stop();
            LOGGER.log(Level.INFO, "Stopped Ganglia reporter to {0}:{1} with prefix {2}", new Object[]{
                    s.getHostname(), s.getPort(), StringUtils.isBlank(s.getPrefix()) ? hostname : s.getPrefix()
            });
        }
    }
}
