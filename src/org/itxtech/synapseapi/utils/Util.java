package org.itxtech.synapseapi.utils;

import sun.misc.BASE64Encoder;

import java.nio.ByteBuffer;

/**
 * Created by boybook on 16/6/25.
 */
public class Util {

    /**
     * base 64 encode
     * @param bytes 待编码的byte[]
     * @return 编码后的base 64 code
     */
    public static String base64Encode(byte[] bytes){
        return new BASE64Encoder().encode(bytes);
    }

}
