package io.github.exascope;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SettingsStore {
    private static final String ALIAS = "exascope.api.v1";
    private final SharedPreferences preferences;
    public String keyError = "";

    public SettingsStore(Context context) {
        preferences = context.getSharedPreferences("exascope_settings", Context.MODE_PRIVATE);
    }
    public SearchConfig load() {
        String key = "";
        try { key = decrypt(preferences.getString("api_key", "")); keyError = ""; }
        catch (Exception error) { keyError = "无法解密已保存的 Key，请重新填写并保存"; }
        return new SearchConfig(preferences.getString("endpoint", SearchConfig.DEFAULT_ENDPOINT), key,
                preferences.getString("scope", ""), preferences.getString("template", SearchConfig.DEFAULT_TEMPLATE),
                preferences.getString("category", ""), preferences.getString("domains", ""),
                preferences.getString("type", "auto"), preferences.getInt("count", 10));
    }
    public void save(SearchConfig config) throws Exception {
        config.validate();
        String encrypted = encrypt(config.apiKey);
        boolean saved = preferences.edit().putString("endpoint", config.endpoint)
                .putString("api_key", encrypted).putString("scope", config.scope).putString("template", config.template)
                .putString("category", config.category).putString("domains", config.domains)
                .putString("type", config.searchType).putInt("count", config.resultCount).commit();
        if (!saved) throw new IllegalStateException("保存失败，请检查设备存储空间");
        keyError = "";
    }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private static String encrypt(String plaintext) throws Exception {
        if (plaintext.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] bytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
    private static String decrypt(String encrypted) throws Exception {
        if (encrypted.isEmpty()) return "";
        String[] parts = encrypted.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted key");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
