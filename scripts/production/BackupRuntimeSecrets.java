import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

/** Private pipe to the root backup process; never run in an interactive log. */
class BackupRuntimeSecrets {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(System.in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String password = properties.getProperty("NPDMS_REDIS_PASSWORD",
                properties.getProperty("REDIS_PASSWORD"));
        if (password == null || password.isBlank()) throw new IllegalStateException("Redis credential absent");
        System.out.print(Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
    }
}
