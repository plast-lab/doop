package org.clyze.doop.system

import java.security.MessageDigest

class CheckSum {

    static String checksum(String s, String algorithm) {
        MessageDigest digest = MessageDigest.getInstance(algorithm)
        return toHex(digest.digest(s.getBytes("UTF-8")))
    }

    static String checksum(File f, String algorithm) {
        return f.withInputStream { InputStream input ->
            return checksum(input, algorithm)
        }
    }

    static String checksum(InputStream input, String algorithm) {
        MessageDigest digest = MessageDigest.getInstance(algorithm)
        byte[] bytes = new byte[4096]
        int bytesRead
        while ((bytesRead = input.read(bytes)) != -1) {
            digest.update(bytes, 0, bytesRead)
        }
        return toHex(digest.digest())
    }

    /**
     * Returns the hex string of the input bytes.
     */
    private static String toHex(byte[] bytes) {
        BigInteger number = new BigInteger(1, bytes)
        String checksum = number.toString(16)
        int len = checksum.length()
        while (len < 32) {
            checksum = "0" + checksum
        }
        return checksum
    }
}
