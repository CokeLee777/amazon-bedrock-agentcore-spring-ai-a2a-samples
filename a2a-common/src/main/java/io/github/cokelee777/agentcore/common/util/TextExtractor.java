package io.github.cokelee777.agentcore.common.util;

import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;

/**
 * Utility for extracting plain text from A2A {@link Message} and {@link Task} objects.
 *
 * <p>
 * Only {@link TextPart} parts are collected; other part kinds are silently ignored.
 * </p>
 */
public final class TextExtractor {

	private TextExtractor() {
	}

	/**
	 * Concatenates the text of all {@link TextPart} parts in the given message.
	 * @param message the A2A message whose parts to extract
	 * @return the concatenated text, or an empty string if no text parts are present
	 */
	public static String extractFromMessage(Message message) {
		StringBuilder textBuilder = new StringBuilder();
		for (Part<?> part : message.getParts()) {
			if (part instanceof TextPart textPart) {
				textBuilder.append(textPart.getText());
			}
		}
		return textBuilder.toString();
	}

	/**
	 * Concatenates the text of all {@link TextPart} parts across every artifact in the
	 * given task.
	 * @param task the A2A task whose artifacts to extract
	 * @return the concatenated text, or an empty string if the task has no artifacts or
	 * text parts
	 */
	public static String extractFromTask(Task task) {
		if (task.getArtifacts() == null) {
			return "";
		}
		StringBuilder textBuilder = new StringBuilder();
		task.getArtifacts().forEach(artifact -> artifact.parts().forEach(part -> {
			if (part instanceof TextPart textPart) {
				textBuilder.append(textPart.getText());
			}
		}));
		return textBuilder.toString();
	}

}
