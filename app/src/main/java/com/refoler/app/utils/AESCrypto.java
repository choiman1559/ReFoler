package com.refoler.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.android.gms.common.util.Hex;
import com.refoler.app.Applications;
import com.refoler.app.ui.PrefsKeyConst;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESCrypto {
    public static JSONObject processEncrypt(Context context, JSONObject object) throws Exception {
        SharedPreferences prefs = Applications.getPrefs(context);
        String rawKey = prefs.getString(PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE, "");

        if(prefs.getBoolean(PrefsKeyConst.PREFS_KEY_PASSWORD_ENABLED, false) && !rawKey.isEmpty()) {
            JSONObject encryptedObj = new JSONObject();
            encryptedObj.put(PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE, AESCrypto.encrypt(object.toString(), parseAESToken(rawKey)));
            return encryptedObj;
        } else {
            return object;
        }
    }

    public static JSONObject processDecrypt(Context context, JSONObject object) throws Exception {
        SharedPreferences prefs = Applications.getPrefs(context);
        String rawKey = prefs.getString(PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE, "");

        if(object.has(PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE)) {
            if(prefs.getBoolean(PrefsKeyConst.PREFS_KEY_PASSWORD_ENABLED, false) && !rawKey.isEmpty()) {
                return new JSONObject(AESCrypto.decrypt(object.getString(PrefsKeyConst.PREFS_KEY_PASSWORD_VALUE), parseAESToken(rawKey)));
            } else {
                throw new Exception("Password is not set");
            }
        } else {
            return object;
        }
    }

    public static String encrypt(String plain, String TOKEN_KEY) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TOKEN_KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] ivAndCipherText = getCombinedArray(iv, cipherText);
        return Base64.encodeToString(ivAndCipherText, Base64.NO_WRAP);
    }

    public static String decrypt(String encoded, String TOKEN_KEY) throws GeneralSecurityException {
        byte[] ivAndCipherText = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = Arrays.copyOfRange(ivAndCipherText, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(ivAndCipherText, 16, ivAndCipherText.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(TOKEN_KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private static byte[] getCombinedArray(byte[] one, byte[] two) {
        byte[] combined = new byte[one.length + two.length];
        for (int i = 0; i < combined.length; ++i) {
            combined[i] = i < one.length ? one[i] : two[i - one.length];
        }
        return combined;
    }

    public static String parseAESToken(String string) {
        if (string.length() == 32) return string;
        string += "D~L*e/`/Q*a&h~e0jy$zU!sg?}X`CU*I";
        return string.substring(0, 32);
    }

    public static String shaAndHex(String plainText) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(plainText.getBytes(StandardCharsets.UTF_8));
        return Hex.bytesToStringLowercase(md.digest());
    }
}