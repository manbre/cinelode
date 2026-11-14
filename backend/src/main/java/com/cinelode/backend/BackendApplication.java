package com.cinelode.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots the Cinelode Spring application. */
@SpringBootApplication
public class BackendApplication {

	/** Launches the Spring Boot application. */
	public static void main(String[] args) {
		if (args.length == 1 && "--generate-password-hash".equals(args[0])) {
			PasswordHashGenerator.run();
			return;
		}
		SpringApplication.run(BackendApplication.class, args);
	}

}
