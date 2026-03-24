package io.github.cokelee777.agent.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the Delivery A2A agent.
 *
 * <p>
 * Handles the {@code track_delivery} skill. Beans are defined in
 * {@link DeliveryAgentConfiguration}.
 * </p>
 */
@SpringBootApplication
public class DeliveryAgentApplication {

	/**
	 * Starts the Delivery Agent.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(DeliveryAgentApplication.class, args);
	}

}
