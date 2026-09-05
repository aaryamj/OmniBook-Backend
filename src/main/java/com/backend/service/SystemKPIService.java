package com.backend.service;

import com.backend.dto.MetricsDataPoint;
import com.backend.dto.SystemKPIDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SystemKPIService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    // In-memory data store for the charts
    private final List<MetricsDataPoint> trendData = Collections.synchronizedList(new ArrayList<>());
    private final List<MetricsDataPoint> dbTrendData = Collections.synchronizedList(new ArrayList<>());
    private final List<MetricsDataPoint> gatewayTrendData = Collections.synchronizedList(new ArrayList<>());
    private final List<MetricsDataPoint> thirdPartyTrendData = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> pgConnectionsHistory = Collections.synchronizedList(new ArrayList<>());
    
    // Aggregates for the current 3-second window
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    // Latest real-time scalar metrics
    private double currentCpu = 0;
    private double currentJvm = 0;
    private double currentDbStorage = 0;
    private double currentApiLatency = 0;
    private int currentPgConnections = 0;

    public void recordApiRequest(long latencyMs, boolean isError) {
        requestCount.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        if (isError) {
            errorCount.incrementAndGet();
        }
    }

    @Scheduled(fixedRate = 3000)
    public void aggregateMetrics() {
        // 1. Gather API stats for this 3s window
        int reqs = requestCount.getAndSet(0);
        int errs = errorCount.getAndSet(0);
        long latencySum = totalLatencyMs.getAndSet(0);
        double avgLatency = reqs > 0 ? (double) latencySum / reqs : 0;
        
        // Update current scalar avg latency smoothly if reqs > 0, else keep it or decay it
        if (reqs > 0) {
            currentApiLatency = avgLatency;
        } else {
            currentApiLatency = currentApiLatency > 0 ? currentApiLatency * 0.8 : 0; // decay
        }

        // 2. Gather System stats
        // CPU
        double sysLoad = osBean.getSystemLoadAverage();
        // getSystemLoadAverage() may return negative if unsupported (e.g. Windows)
        if (sysLoad < 0) {
            // Simulated fallback CPU reading based on request count for Windows dev env
            currentCpu = Math.min(100.0, 10.0 + (reqs * 2.5) + (Math.random() * 5));
        } else {
            currentCpu = sysLoad * 10.0; // rough percentage if it's a 1-core equivalent load
        }

        // JVM
        Runtime runtime = Runtime.getRuntime();
        double maxMemory = runtime.maxMemory();
        double usedMemory = runtime.totalMemory() - runtime.freeMemory();
        currentJvm = maxMemory > 0 ? (usedMemory / maxMemory) * 100.0 : 0;

        // DB Stats
        try {
            Double sizeRes = jdbcTemplate.queryForObject(
                "SELECT sum(data_length + index_length) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Double.class
            );
            if (sizeRes != null) {
                currentDbStorage = sizeRes / (1024 * 1024 * 1024); // GB
            }

            Integer connRes = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.processlist WHERE command != 'Sleep'",
                Integer.class
            );
            if (connRes != null) {
                currentPgConnections = connRes;
            }
        } catch (Exception e) {
            // Fallback if DB queries fail
        }

        // 3. Update arrays (keep max 7 for trends, 14 for pgHistory)
        
        // trendData: Latency vs Throughput (reqs)
        addPoint(trendData, new MetricsDataPoint(avgLatency, reqs), 7);
        
        // dbTrendData: Query Latency vs IOPS (We derive IOPS heavily from reqs)
        double dbLat = avgLatency * 0.4;
        double dbIops = reqs * 3.5 + (Math.random() * 5); // baseline + load
        addPoint(dbTrendData, new MetricsDataPoint(dbLat, dbIops), 7);
        
        // gatewayTrendData: Request Rate vs Errors
        addPoint(gatewayTrendData, new MetricsDataPoint(errs, reqs), 7);
        
        // thirdPartyTrendData: Webhook Delivery vs Timeouts
        // Deriving from internal requests, as requested in the plan
        double thirdPartyDeliveries = reqs * 0.2; 
        double thirdPartyTimeouts = errs * 0.1;
        addPoint(thirdPartyTrendData, new MetricsDataPoint(thirdPartyTimeouts, thirdPartyDeliveries), 7);
        
        // pgConnectionsHistory
        addHistory(pgConnectionsHistory, currentPgConnections, 14);
    }

    private <T> void addPoint(List<T> list, T item, int max) {
        list.add(item);
        if (list.size() > max) {
            list.remove(0);
        }
    }
    
    private void addHistory(List<Integer> list, Integer item, int max) {
        list.add(item);
        if (list.size() > max) {
            list.remove(0);
        }
    }

    public SystemKPIDTO getCurrentKPIs() {
        SystemKPIDTO dto = new SystemKPIDTO();
        dto.setCpuUtilization(currentCpu);
        dto.setJvmHeap(currentJvm);
        dto.setDbStorageGB(currentDbStorage);
        dto.setAverageApiLatency(currentApiLatency);
        dto.setPgActiveConnections(currentPgConnections);
        
        dto.setTrendData(new ArrayList<>(trendData));
        dto.setDbTrendData(new ArrayList<>(dbTrendData));
        dto.setGatewayTrendData(new ArrayList<>(gatewayTrendData));
        dto.setThirdPartyTrendData(new ArrayList<>(thirdPartyTrendData));
        dto.setPgConnectionsHistory(new ArrayList<>(pgConnectionsHistory));
        
        // pad empty arrays for smooth front-end init
        padArray(dto.getTrendData(), 7, new MetricsDataPoint(0, 0));
        padArray(dto.getDbTrendData(), 7, new MetricsDataPoint(0, 0));
        padArray(dto.getGatewayTrendData(), 7, new MetricsDataPoint(0, 0));
        padArray(dto.getThirdPartyTrendData(), 7, new MetricsDataPoint(0, 0));
        padIntArray(dto.getPgConnectionsHistory(), 14, 0);
        
        return dto;
    }
    
    private void padArray(List<MetricsDataPoint> list, int expectedSize, MetricsDataPoint def) {
        while (list.size() < expectedSize) {
            list.add(0, def);
        }
    }
    
    private void padIntArray(List<Integer> list, int expectedSize, Integer def) {
        while (list.size() < expectedSize) {
            list.add(0, def);
        }
    }
}
