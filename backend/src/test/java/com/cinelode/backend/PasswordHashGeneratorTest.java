package com.cinelode.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesSpringSecurityCompatiblePasswordHashes() {
        String encoded = PasswordHashGenerator.encode(
                "safe-test-password".toCharArray(),
                "safe-test-password".toCharArray());

        assertTrue(encoded.startsWith("{bcrypt}"));
        assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches("safe-test-password", encoded));
    }

    @Test
    void rejectsPasswordConfirmationMismatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PasswordHashGenerator.encode(
                        "first-password".toCharArray(),
                        "different-password".toCharArray()));
    }

    @Test
    void savesHashAndPreservesExistingEnvironmentSettings() throws IOException {
        Path environmentFile = temporaryDirectory.resolve(".env");
        Files.writeString(environmentFile, "OMDB_API_KEY=test-key\n");

        PasswordHashGenerator.saveHash(environmentFile, "{bcrypt}encoded-hash");

        String result = Files.readString(environmentFile);
        assertTrue(result.contains("OMDB_API_KEY=test-key"));
        assertTrue(result.contains("CINELODE_PASSWORD_HASH={bcrypt}encoded-hash"));
    }

    @Test
    void replacesExistingHashInsteadOfCreatingDuplicateSettings() throws IOException {
        Path environmentFile = temporaryDirectory.resolve(".env");
        Files.writeString(environmentFile,
                "OMDB_API_KEY=test-key\nCINELODE_PASSWORD_HASH={bcrypt}old-hash\n");

        PasswordHashGenerator.saveHash(environmentFile, "{bcrypt}new-hash");

        String result = Files.readString(environmentFile);
        assertTrue(result.contains("CINELODE_PASSWORD_HASH={bcrypt}new-hash"));
        assertTrue(!result.contains("old-hash"));
    }
}
