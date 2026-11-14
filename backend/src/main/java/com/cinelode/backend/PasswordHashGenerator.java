package com.cinelode.backend;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.Console;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/** Generates an encoded login password without starting the application. */
public final class PasswordHashGenerator {
    private static final String PROPERTY = "CINELODE_PASSWORD_HASH";

    private PasswordHashGenerator() {
    }

    public static void run() {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                    "Password hash generation requires an interactive terminal.");
        }

        char[] password = console.readPassword("New Cinelode password: ");
        if (password == null) {
            throw new IllegalStateException("Password input ended before confirmation.");
        }
        char[] confirmation = console.readPassword("Confirm password: ");
        if (confirmation == null) {
            Arrays.fill(password, '\0');
            throw new IllegalStateException("Password input ended before confirmation.");
        }
        try {
            String encoded = encode(password, confirmation);
            Path environmentFile = environmentFile();
            try {
                saveHash(environmentFile, encoded);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not securely save " + PROPERTY + " to "
                                + environmentFile.toAbsolutePath()
                                + ". Check file permissions and remove duplicate entries if present.",
                        exception);
            }
            console.printf("Saved %s to %s.%n", PROPERTY, environmentFile.toAbsolutePath());
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    static String encode(char[] password, char[] confirmation) {
        if (password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
        if (!Arrays.equals(password, confirmation)) {
            throw new IllegalArgumentException("Password confirmation does not match.");
        }

        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return encoder.encode(new String(password));
    }

    static void saveHash(Path environmentFile, String encoded) throws IOException {
        Path target = environmentFile.toAbsolutePath();
        Path directory = target.getParent();
        Files.createDirectories(directory);

        List<String> lines = Files.exists(target)
                ? Files.readAllLines(target)
                : List.of();
        Pattern propertyLine = Pattern.compile("^\\s*" + Pattern.quote(PROPERTY) + "\\s*=.*$");
        long matchingLines = lines.stream().filter(line -> propertyLine.matcher(line).matches()).count();
        if (matchingLines > 1) {
            throw new IOException("Multiple " + PROPERTY + " entries exist in " + target
                    + "; remove duplicates before generating a new hash.");
        }

        String replacement = PROPERTY + "=" + encoded;
        List<String> updated = new ArrayList<>(lines.stream()
                .map(line -> propertyLine.matcher(line).matches() ? replacement : line)
                .toList());
        if (matchingLines == 0) {
            updated.add(replacement);
        }

        Path temporary = Files.createTempFile(directory, ".cinelode-env-", ".tmp");
        try {
            setPrivatePermissions(temporary);
            Files.writeString(temporary, String.join(System.lineSeparator(), updated)
                    + System.lineSeparator());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setPrivatePermissions(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path environmentFile() {
        return Files.isDirectory(Path.of("src/main/resources"))
                ? Path.of(".env")
                : Path.of("backend/.env");
    }

    private static void setPrivatePermissions(Path path) throws IOException {
        if (Files.getFileAttributeView(
                path, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }
    }
}
