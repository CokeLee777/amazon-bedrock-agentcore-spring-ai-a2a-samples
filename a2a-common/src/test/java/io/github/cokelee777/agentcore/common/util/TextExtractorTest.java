package io.github.cokelee777.agentcore.common.util;

import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TextExtractor}.
 */
@ExtendWith(MockitoExtension.class)
class TextExtractorTest {

	@Mock
	private Message mockMessage;

	@Test
	void extractFromMessage_singleTextPart_returnsText() {
		Message message = new Message.Builder().role(Message.Role.USER).parts(new TextPart("hello")).build();

		assertThat(TextExtractor.extractFromMessage(message)).isEqualTo("hello");
	}

	@Test
	void extractFromMessage_multipleTextParts_concatenatesAll() {
		Message message = new Message.Builder().role(Message.Role.USER)
			.parts(new TextPart("hello"), new TextPart(" world"))
			.build();

		assertThat(TextExtractor.extractFromMessage(message)).isEqualTo("hello world");
	}

	@Test
	@SuppressWarnings("unchecked")
	void extractFromMessage_noTextParts_returnsEmptyString() {
		when(mockMessage.getParts()).thenReturn(List.of());

		assertThat(TextExtractor.extractFromMessage(mockMessage)).isEmpty();
	}

	@Test
	void extractFromTask_nullArtifacts_returnsEmptyString() {
		Task task = new Task.Builder().id("task-1")
			.contextId("ctx-1")
			.status(new TaskStatus(TaskState.COMPLETED))
			.build();

		assertThat(TextExtractor.extractFromTask(task)).isEmpty();
	}

	@Test
	void extractFromTask_emptyArtifactsList_returnsEmptyString() {
		Task task = new Task.Builder().id("task-1")
			.contextId("ctx-1")
			.status(new TaskStatus(TaskState.COMPLETED))
			.artifacts(List.of())
			.build();

		assertThat(TextExtractor.extractFromTask(task)).isEmpty();
	}

	@Test
	void extractFromTask_artifactsWithTextParts_concatenatesAllText() {
		Artifact artifact1 = new Artifact.Builder().artifactId("art-1")
			.parts(new TextPart("order"), new TextPart(" info"))
			.build();
		Artifact artifact2 = new Artifact.Builder().artifactId("art-2").parts(new TextPart(" delivered")).build();
		Task task = new Task.Builder().id("task-1")
			.contextId("ctx-1")
			.status(new TaskStatus(TaskState.COMPLETED))
			.artifacts(List.of(artifact1, artifact2))
			.build();

		assertThat(TextExtractor.extractFromTask(task)).isEqualTo("order info delivered");
	}

}
