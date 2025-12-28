import java.io.*;
import java.util.Properties;

public class PreferenceManager {
    private static final String PREFERENCE_FILE = "MSH/preferences.properties";
    private Properties preferences;
    
    public PreferenceManager() {
        preferences = new Properties();
        loadPreferences();
    }
    
    private void loadPreferences() {
        File file = new File(PREFERENCE_FILE);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                preferences.load(input);
                Logger.info("Preferences loaded successfully from " + PREFERENCE_FILE, "PreferenceManager");
            } catch (IOException e) {
                Logger.error("Failed to load preferences: " + e.getMessage(), "PreferenceManager");
            }
        } else {
            Logger.info("Preferences file does not exist, using default values", "PreferenceManager");
        }
    }
    
    private void savePreferences() {
        File file = new File(PREFERENCE_FILE);
        File parentDir = file.getParentFile();
        if (parentDir == null || !parentDir.exists() && !parentDir.mkdirs()) {
            Logger.error("Failed to create preference directory", "PreferenceManager");
            return;
        }
        
        try (OutputStream output = new FileOutputStream(file)) {
            preferences.store(output, "User Preferences");
            Logger.info("Preferences saved successfully to " + PREFERENCE_FILE, "PreferenceManager");
        } catch (IOException e) {
            Logger.error("Failed to save preferences: " + e.getMessage(), "PreferenceManager");
        }
    }
    
    public boolean shouldCheckForUpdates() {
        return preferences.getProperty("checkUpdates", "true").equals("true");
    }
    
    public void setCheckForUpdates(boolean check) {
        preferences.setProperty("checkUpdates", String.valueOf(check));
        savePreferences();
    }
    
    public boolean shouldShowUpdateDialog() {
        return preferences.getProperty("showUpdateDialog", "true").equals("true");
    }
    
    public void setShowUpdateDialog(boolean show) {
        preferences.setProperty("showUpdateDialog", String.valueOf(show));
        savePreferences();
    }
    
    public void setLastUpdateCheck(long timestamp) {
        preferences.setProperty("lastUpdateCheck", String.valueOf(timestamp));
        savePreferences();
    }
    
    public long getLastUpdateCheck() {
        String value = preferences.getProperty("lastUpdateCheck", "0");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getPendingDeleteOldVersion() {
        return preferences.getProperty("pendingDeleteOldVersion", null);
    }

    public void setPendingDeleteOldVersion(String oldVersionPath) {
        if (oldVersionPath != null && !oldVersionPath.isEmpty()) {
            preferences.setProperty("pendingDeleteOldVersion", oldVersionPath);
            savePreferences();
        }
    }

    public void clearPendingDeleteOldVersion() {
        preferences.remove("pendingDeleteOldVersion");
        savePreferences();
    }
}