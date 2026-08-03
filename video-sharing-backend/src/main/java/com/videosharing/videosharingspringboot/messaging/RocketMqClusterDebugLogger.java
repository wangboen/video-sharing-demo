package com.videosharing.videosharingspringboot.messaging;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "rocketmq.debug.cluster-list.enabled", havingValue = "true")
public class RocketMqClusterDebugLogger implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(RocketMqClusterDebugLogger.class);

    private final String nameServer;
    private final long timeoutMs;

    public RocketMqClusterDebugLogger(@Value("${rocketmq.name-server:}") String nameServer,
                                      @Value("${rocketmq.debug.cluster-list.timeout-ms:1500}") long timeoutMs) {
        this.nameServer = nameServer;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (nameServer == null || nameServer.isBlank()) {
            logger.warn("RocketMQ cluster debug skipped because rocketmq.name-server is empty");
            return;
        }

        DefaultMQAdminExt admin = new DefaultMQAdminExt("video-sharing-cluster-debug-" + UUID.randomUUID(), timeoutMs);
        admin.setNamesrvAddr(nameServer);
        admin.setInstanceName("video-sharing-cluster-debug-" + System.currentTimeMillis());

        try {
            admin.start();
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            logger.info("{}", formatClusterInfo(clusterInfo));
        } catch (Exception exception) {
            logger.warn("RocketMQ cluster debug failed for nameServer={}", nameServer, exception);
        } finally {
            admin.shutdown();
        }
    }

    private String formatClusterInfo(ClusterInfo clusterInfo) {
        StringBuilder output = new StringBuilder();
        output.append(System.lineSeparator())
                .append("========== RocketMQ Cluster Debug ==========").append(System.lineSeparator())
                .append("NameServer: ").append(nameServer).append(System.lineSeparator())
                .append("#Cluster Name\t#Broker Name\t#BID\t#Addr").append(System.lineSeparator());

        Map<String, BrokerData> brokers = new TreeMap<>(clusterInfo.getBrokerAddrTable());
        brokers.values().forEach(broker -> {
            Map<Long, String> addresses = new TreeMap<>(broker.getBrokerAddrs());
            addresses.forEach((brokerId, address) -> output
                    .append(broker.getCluster()).append('\t')
                    .append(broker.getBrokerName()).append('\t')
                    .append(brokerId).append('\t')
                    .append(address).append(System.lineSeparator()));
        });

        output.append("============================================");
        return output.toString();
    }
}
