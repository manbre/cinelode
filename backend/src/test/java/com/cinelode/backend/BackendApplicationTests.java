package com.cinelode.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:sqlite::memory:",
		"cinelode.media.root=target/classes",
		"cinelode.series.root=target/classes",
		"cinelode.security.username=test-user",
		"cinelode.security.password-hash={noop}test-password",
		"server.ssl.enabled=false",
		"server.address=127.0.0.1",
		"server.servlet.session.cookie.secure=false"
})
class BackendApplicationTests {

	/** Verifies that the Spring application context starts successfully. */
	@Test
	void contextLoads() {
	}

}
