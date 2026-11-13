package com.restaurantmanager.gateway;

import com.restaurantmanager.gateway.config.SecretBox;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local attendance gateway. Runs on a computer on the restaurant network, reads the biometric devices, keeps every event
 * in a local SQLite queue and sends it to the Restaurant Manager backend whenever the internet is available.
 *
 * <pre>
 *   java -jar attendance-gateway.jar                    # run
 *   java -jar attendance-gateway.jar encrypt "secret"   # print an enc:... value for a device password (needs GATEWAY_MASTER_KEY)
 *   java -jar attendance-gateway.jar new-master-key     # print a new random GATEWAY_MASTER_KEY
 * </pre>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && "new-master-key".equals(args[0])) {
            System.out.println(SecretBox.newMasterKey());
            return;
        }
        if (args.length >= 2 && "encrypt".equals(args[0])) {
            System.out.println(SecretBox.fromEnvironment().encrypt(args[1]));
            return;
        }
        // SQLite needs the folder to exist before the database opens.
        String dataDir = System.getProperty("gateway.data-dir",
                System.getenv().getOrDefault("GATEWAY_DATA_DIR", "./gateway-data"));
        Files.createDirectories(Path.of(dataDir));
        SpringApplication.run(GatewayApplication.class, args);
    }
}
