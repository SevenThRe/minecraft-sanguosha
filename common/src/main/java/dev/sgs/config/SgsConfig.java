package dev.sgs.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.sgs.Sgs;

/**
 * Server-side runtime configuration loaded from {@code sgs-config.toml}.
 *
 * <p>The file uses a tiny flat TOML subset (key = value pairs with comments), which keeps the
 * loader pure Java and identical across Fabric and Forge. Invalid or missing values fall back to
 * defaults and produce a warning.
 */
public final class SgsConfig {

  public static final String FILE_NAME = "sgs-config.toml";

  // Non-configurable for the MVP; kept here so all timing rules live together.
  public static final int GENERAL_SELECTION_TIMEOUT_SECONDS = 30;

  public static final boolean DEFAULT_ENABLE_CARD_TABLE = true;
  public static final boolean DEFAULT_ENABLE_COMMAND = true;
  public static final int DEFAULT_RESPONSE_TIMEOUT_SECONDS = 10;
  public static final int DEFAULT_TURN_TIMEOUT_SECONDS = 15;
  public static final boolean DEFAULT_DEBUG_LOG = false;

  private static volatile SgsConfig instance =
      new SgsConfig(
          DEFAULT_ENABLE_CARD_TABLE,
          DEFAULT_ENABLE_COMMAND,
          DEFAULT_RESPONSE_TIMEOUT_SECONDS,
          DEFAULT_TURN_TIMEOUT_SECONDS,
          DEFAULT_DEBUG_LOG);

  private final boolean enableCardTable;
  private final boolean enableCommand;
  private final int responseTimeoutSeconds;
  private final int turnTimeoutSeconds;
  private final boolean debugLog;

  public SgsConfig(
      boolean enableCardTable,
      boolean enableCommand,
      int responseTimeoutSeconds,
      int turnTimeoutSeconds,
      boolean debugLog) {
    this.enableCardTable = enableCardTable;
    this.enableCommand = enableCommand;
    this.responseTimeoutSeconds = responseTimeoutSeconds;
    this.turnTimeoutSeconds = turnTimeoutSeconds;
    this.debugLog = debugLog;
  }

  public static SgsConfig get() {
    return instance;
  }

  public static SgsConfig defaults() {
    return new SgsConfig(
        DEFAULT_ENABLE_CARD_TABLE,
        DEFAULT_ENABLE_COMMAND,
        DEFAULT_RESPONSE_TIMEOUT_SECONDS,
        DEFAULT_TURN_TIMEOUT_SECONDS,
        DEFAULT_DEBUG_LOG);
  }

  public boolean enableCardTable() {
    return enableCardTable;
  }

  public boolean enableCommand() {
    return enableCommand;
  }

  public int responseTimeoutSeconds() {
    return responseTimeoutSeconds;
  }

  public int turnTimeoutSeconds() {
    return turnTimeoutSeconds;
  }

  /** Verbose match/network diagnostics. Release builds run with this off. */
  public boolean debugLog() {
    return debugLog;
  }

  /** Loads the config file, creating or repairing it, and publishes the result. */
  public static synchronized SgsConfig load(Path configDirectory) {
    Path file = configDirectory.resolve(FILE_NAME);
    Map<String, String> raw = readFlatToml(file);

    boolean cardTable = parseBoolean(raw, "enableCardTable", DEFAULT_ENABLE_CARD_TABLE);
    boolean command = parseBoolean(raw, "enableCommand", DEFAULT_ENABLE_COMMAND);
    int responseTimeout =
        parsePositiveInt(raw, "responseTimeoutSeconds", DEFAULT_RESPONSE_TIMEOUT_SECONDS);
    int turnTimeout = parsePositiveInt(raw, "turnTimeoutSeconds", DEFAULT_TURN_TIMEOUT_SECONDS);
    boolean debugLog = parseBoolean(raw, "debugLog", DEFAULT_DEBUG_LOG);

    SgsConfig loaded = new SgsConfig(cardTable, command, responseTimeout, turnTimeout, debugLog);
    writeDefaultFile(file, loaded);
    instance = loaded;

    Sgs.LOGGER.info(
        "[SGS-CONFIG] loaded: enableCardTable={}, enableCommand={}, responseTimeoutSeconds={},"
            + " turnTimeoutSeconds={}, debugLog={}, generalSelectionTimeoutSeconds={}",
        loaded.enableCardTable,
        loaded.enableCommand,
        loaded.responseTimeoutSeconds,
        loaded.turnTimeoutSeconds,
        loaded.debugLog,
        GENERAL_SELECTION_TIMEOUT_SECONDS);
    return loaded;
  }

  private static Map<String, String> readFlatToml(Path file) {
    Map<String, String> values = new LinkedHashMap<>();
    if (!Files.isRegularFile(file)) {
      return values;
    }
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      Sgs.LOGGER.warn(
          "[SGS-CONFIG] failed to read {} ({}), using defaults", FILE_NAME, e.toString());
      return values;
    }
    int lineNo = 0;
    for (String line : lines) {
      lineNo++;
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      if (trimmed.startsWith("[")) {
        // Only flat key/value pairs are supported; sections are unexpected.
        Sgs.LOGGER.warn(
            "[SGS-CONFIG] {} line {}: sections are not supported, ignoring: {}",
            FILE_NAME,
            lineNo,
            trimmed);
        continue;
      }
      int eq = trimmed.indexOf('=');
      if (eq <= 0) {
        Sgs.LOGGER.warn(
            "[SGS-CONFIG] {} line {}: expected key = value, ignoring: {}",
            FILE_NAME,
            lineNo,
            trimmed);
        continue;
      }
      String key = trimmed.substring(0, eq).trim();
      String value = trimmed.substring(eq + 1).trim();
      if (values.put(key, value) != null) {
        Sgs.LOGGER.warn("[SGS-CONFIG] {} line {}: duplicate key '{}'", FILE_NAME, lineNo, key);
      }
    }
    return values;
  }

  private static boolean parseBoolean(Map<String, String> raw, String key, boolean fallback) {
    String value = raw.get(key);
    if (value == null) {
      Sgs.LOGGER.warn("[SGS-CONFIG] missing '{}', using default {}", key, fallback);
      return fallback;
    }
    String normalized = value.toLowerCase(Locale.ROOT);
    if ("true".equals(normalized)) {
      return true;
    }
    if ("false".equals(normalized)) {
      return false;
    }
    Sgs.LOGGER.warn(
        "[SGS-CONFIG] invalid boolean '{}' for '{}', using default {}", value, key, fallback);
    return fallback;
  }

  private static int parsePositiveInt(Map<String, String> raw, String key, int fallback) {
    String value = raw.get(key);
    if (value == null) {
      Sgs.LOGGER.warn("[SGS-CONFIG] missing '{}', using default {}", key, fallback);
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) {
        Sgs.LOGGER.warn(
            "[SGS-CONFIG] non-positive value '{}' for '{}', using default {}",
            value,
            key,
            fallback);
        return fallback;
      }
      return parsed;
    } catch (NumberFormatException e) {
      Sgs.LOGGER.warn(
          "[SGS-CONFIG] invalid integer '{}' for '{}', using default {}", value, key, fallback);
      return fallback;
    }
  }

  private static void writeDefaultFile(Path file, SgsConfig config) {
    String content =
        "# MC-SGS configuration. Invalid or missing values fall back to defaults.\n"
            + "# Enable the card table block entry (right-click to open the room interface).\n"
            + "enableCardTable = "
            + config.enableCardTable
            + "\n"
            + "# Enable the /sgs command.\n"
            + "enableCommand = "
            + config.enableCommand
            + "\n"
            + "# Seconds a player has to respond to prompts (dodge, nullify, rescue).\n"
            + "responseTimeoutSeconds = "
            + config.responseTimeoutSeconds
            + "\n"
            + "# Seconds for the play and discard phases of each turn.\n"
            + "turnTimeoutSeconds = "
            + config.turnTimeoutSeconds
            + "\n"
            + "# Verbose match diagnostics (hand-level debug logs); keep false in production.\n"
            + "debugLog = "
            + config.debugLog
            + "\n";
    try {
      Files.createDirectories(file.getParent());
      Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      Sgs.LOGGER.warn("[SGS-CONFIG] failed to write {}: {}", FILE_NAME, e.toString());
    }
  }
}
