package com.comp90018.contexttunes.utils;

import android.graphics.Bitmap;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public final class ImageLabelsHasher {
    private ImageLabelsHasher() {}

    public static String hash(Bitmap bmp) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            ByteBuffer buf = ByteBuffer.allocate(bmp.getByteCount());
            bmp.copyPixelsToBuffer(buf);
            md.update(buf.array());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            // last-resort fallback; still stable within process
            return String.valueOf(bmp.hashCode());
        }
    }
}
