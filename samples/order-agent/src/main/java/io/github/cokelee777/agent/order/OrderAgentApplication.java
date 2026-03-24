package io.github.cokelee777.agent.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the Order A2A agent.
 *
 * <p>
 * Handles order-related skills: order list retrieval and cancellability check. Beans are
 * defined in {@link OrderAgentConfiguration}.
 * </p>
 */
@SpringBootApplication
public class OrderAgentApplication {

	/**
	 * Starts the Order Agent.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(OrderAgentApplication.class, args);
	}

}
