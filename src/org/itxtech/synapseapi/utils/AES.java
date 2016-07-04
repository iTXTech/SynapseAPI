package org.itxtech.synapseapi.utils;

import cn.nukkit.Server;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AES {
    // 加密
    public static byte[] Encrypt(String sSrc, String sKey){
        try {
            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");//"算法/模式/补码方式"
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            return cipher.doFinal(sSrc.getBytes("utf-8"));
        }catch (Exception e){
            Server.getInstance().getLogger().logException(e);
            return null;
        }
    }

    // 解密
    public static String Decrypt(byte[] sSrc, String sKey) throws Exception {
        try {
            byte[] raw = sKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            try {
                byte[] original = cipher.doFinal(sSrc);
                return new String(original,"utf-8");
            } catch (Exception e) {
                Server.getInstance().getLogger().logException(e);
                return null;
            }
        } catch (Exception ex) {
            Server.getInstance().getLogger().logException(ex);
            return null;
        }
    }
}
