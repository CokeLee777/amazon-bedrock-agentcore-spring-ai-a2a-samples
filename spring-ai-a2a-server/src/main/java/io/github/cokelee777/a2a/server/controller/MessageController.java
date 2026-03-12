/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.cokelee777.a2a.server.controller;

import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.EventKind;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * REST controller for A2A message sending.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MessageController {

	private final RequestHandler requestHandler;

	/**
	 * Handles sendMessage JSON-RPC requests.
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public SendMessageResponse sendMessage(@RequestBody SendMessageRequest request) throws JSONRPCError {

		MessageSendParams params = request.getParams();
		log.debug("Received sendMessage request - id: {}", request.getId());

		try {
			// Create server call context
			// TODO: Add support for auth context, state, and extensions
			ServerCallContext context = new ServerCallContext(null, // auth context (not
																	// used yet)
					Map.of(), // state
					Set.of() // extensions
			);

			// Delegate to SDK's RequestHandler - handles all protocol logic
			EventKind result = this.requestHandler.onMessageSend(params, context);

			log.debug("Message processed successfully - id: {}", request.getId());
			return new SendMessageResponse(request.getId(), result);
		}
		catch (JSONRPCError e) {
			log.error("Error processing message - id: {}", request.getId(), e);
			throw e;
		}
		catch (Exception e) {
			log.error("Unexpected error processing message - id: {}", request.getId(), e);
			throw new JSONRPCError(-32603, "Internal error: " + e.getMessage(), null);
		}
	}

}
