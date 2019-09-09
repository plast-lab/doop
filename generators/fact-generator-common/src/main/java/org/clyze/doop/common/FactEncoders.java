package org.clyze.doop.common;

/**
 * Created by neville on 15/02/2017.
 */
public class FactEncoders {
    public static String encodeStringConstant(String constant) {
        String raw;
        if(constant.trim().equals(constant) && constant.length() > 0)
            raw = constant;
        else
            raw = "<<\"" + constant + "\">>";
        return raw;
    }
}
