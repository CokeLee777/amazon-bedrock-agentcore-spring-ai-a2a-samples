package io.github.cokelee777.a2a.model.chat.memory.repository.bedrock.agentcore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_SESSION_TOKEN", matches = ".+")
public @interface RequiresAwsCredentials {

}
