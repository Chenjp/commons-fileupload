/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.fileupload2.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;

/**
 * Utility class to decode/encode character set on HTTP Header fields based on RFC 2231. This implementation adheres to RFC 5987 in particular, which was
 * defined for HTTP headers
 * <p>
 * RFC 5987 builds on RFC 2231, but has lesser scope like <a href="https://tools.ietf.org/html/rfc5987#section-3.2">mandatory charset definition</a> and
 * <a href="https://tools.ietf.org/html/rfc5987#section-4">no parameter continuation</a>
 * </p>
 *
 * @see <a href="https://tools.ietf.org/html/rfc2231">RFC 2231</a>
 * @see <a href="https://tools.ietf.org/html/rfc5987">RFC 5987</a>
 */
final class RFC2231Utils {

    /**
     * The Hexadecimal values char array.
     */
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * The Hexadecimal representation of 127.
     */
    private static final byte MASK = 0x7f;

    /**
     * The Hexadecimal representation of 128.
     */
    private static final int MASK_128 = 0x80;

    /**
     * The Hexadecimal decode value.
     */
    private static final byte[] HEX_DECODE = new byte[MASK_128];

    // create a ASCII decoded array of Hexadecimal values
    static {
        for (var i = 0; i < HEX_DIGITS.length; i++) {
            HEX_DECODE[HEX_DIGITS[i]] = (byte) i;
            HEX_DECODE[Character.toLowerCase(HEX_DIGITS[i])] = (byte) i;
        }
    }

    /**
     * Definition of attribute-char per RFC 2231 Section 7.
     * <p>
     * attribute-char := &lt;any (US-ASCII) CHAR except SPACE, CTLs, "*", "'", "%", or tspecials&gt;
     */
    private static final boolean[] IS_ATTRIBUTE_CHAR;

    /**
     * Percent encoded hex digits, only accept 0123456789ABCDEF.
     */
    private static final boolean[] IS_HEX_DIGITS;
    static {
        final int ARRAY_SIZE = 128;

        // tspecials defined in rfc 2616
        String tspecials = "()<>@,;:\\\"/[]?={}";
        String octets = "0123456789ABCDEF";

        IS_HEX_DIGITS = new boolean[ARRAY_SIZE];
        IS_ATTRIBUTE_CHAR = new boolean[ARRAY_SIZE];
        for (int i = 0; i < ARRAY_SIZE; i++) {
            IS_HEX_DIGITS[i] = octets.indexOf(i) >= 0;
            if (i <= 32 || i >= 127) {
                IS_ATTRIBUTE_CHAR[i] = false;
            } else if (i == '*' || i == '\'' || i == '%') {
                IS_ATTRIBUTE_CHAR[i] = false;
            } else if (tspecials.indexOf(i) != -1) {
                IS_ATTRIBUTE_CHAR[i] = false;
            } else {
                IS_ATTRIBUTE_CHAR[i] = true;
            }
        }
    }

	private static boolean isAttributeChar(char c) {
		return c >= 0 && c <= 127 && IS_ATTRIBUTE_CHAR[c];
	}

	private static boolean isHexDigitChar(char c) {
		return c >= 0 && c <= 127 && IS_HEX_DIGITS[c];
	}

    /**
     * Decodes a string of text obtained from a HTTP header as per RFC 2231
     *
     * <strong>Eg 1.</strong> {@code us-ascii'en-us'This%20is%20%2A%2A%2Afun%2A%2A%2A} will be decoded to {@code This is ***fun***}
     *
     * <strong>Eg 2.</strong> {@code iso-8859-1'en'%A3%20rate} will be decoded to {@code £ rate}.
     *
     * <strong>Eg 3.</strong> {@code UTF-8''%C2%A3%20and%20%E2%82%AC%20rates} will be decoded to {@code £ and € rates}.
     *
     * @param encodedText   Text to be decoded has a format of {@code <charset>'<language>'<encoded_value>} and ASCII only
     * @return Decoded text based on charset encoding
     * @throws UnsupportedEncodingException The requested character set wasn't found.
     * @throws IllegalArgumentException if the encodedValue or decodedValue contains illegal characters
     */
    static String decodeText(final String encodedText) throws UnsupportedEncodingException, IllegalArgumentException {
        final var langDelimitStart = encodedText.indexOf('\'');
        if (langDelimitStart == -1) {
            // missing charset
            throw new IllegalArgumentException("RFC2231Utils: Missing charset");
        }
        final var mimeCharset = encodedText.substring(0, langDelimitStart);
        final var langDelimitEnd = encodedText.indexOf('\'', langDelimitStart + 1);
        if (langDelimitEnd == -1) {
            // missing language
            throw new IllegalArgumentException("RFC2231Utils: Unclosed quote");
        }

        String encodedValue = encodedText.substring(langDelimitEnd + 1);
        return decodeValue(encodedValue, getJavaCharset(mimeCharset));
    }

    /**
     * Decodes an encoded parameter value string using a specific charset. The supplied charset is used to determine
     * what characters are represented by any consecutive sequences of the form "<i>{@code %HH}</i>".
     * <p>
     * Note: This implementation will throw an {@link java.lang.IllegalArgumentException} when illegal strings are
     * encountered.
     *
     * @param encodedValue the ASCII {@code String} to decode
     * @param charset      the given charset name
     * @return the newly decoded value {@code String}
     * @throws UnsupportedEncodingException if the named charset is not supported
     * @throws NullPointerException         if {@code encodedValue} or {@code charset} is {@code null}
     * @throws IllegalArgumentException     if the encodedValue or decodedValue contains illegal characters
     * @see URLEncoder#encode(java.lang.String, java.nio.charset.Charset)
     */
    private static String decodeValue(String encodedValue, String charset) throws UnsupportedEncodingException {
        Objects.requireNonNull(charset, "Charset");
        Charset cs;
        try {
            cs = Charset.forName(charset);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException x) {
            throw new UnsupportedEncodingException(charset);
        }
        boolean needToChange = false;
        int numChars = encodedValue.length();
        StringBuilder sb = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;

        char c;
        byte[] bytes = null;
        while (i < numChars) {
            c = encodedValue.charAt(i);
            switch (c) {
            case '%':
                /*
                 * Starting with this instance of %, process all consecutive substrings of the form %xy. Each substring
                 * %xy will yield a byte. Convert all consecutive bytes obtained this way to whatever character(s) they
                 * represent in the provided encoding.
                 */

                try {

                    // (numChars-i)/3 is an upper bound for the number
                    // of remaining bytes
                    if (bytes == null)
                        bytes = new byte[(numChars - i) / 3];
                    int pos = 0;

                    while (((i + 2) < numChars) && (c == '%')) {
                        int v = Integer.parseInt(encodedValue, i + 1, i + 3, 16);
                        if (v < 0)
                            throw new IllegalArgumentException("RFC2231Utils: Illegal hex characters in escape "
                                    + "(%HH) pattern - negative value");

                        if (!(isHexDigitChar(encodedValue.charAt(i + 1))
                                && isHexDigitChar(encodedValue.charAt(i + 2)))) {
                            throw new IllegalArgumentException("RFC2231Utils: Illegal hex characters in escape "
                                    + "(%HH) pattern - only 0-9 / A-F allowed");
                        }
                        bytes[pos++] = (byte) v;
                        i += 3;
                        if (i < numChars)
                            c = encodedValue.charAt(i);
                    }

                    // A trailing, incomplete byte encoding such as
                    // "%x" will cause an exception to be thrown

                    if ((i < numChars) && (c == '%'))
                        throw new IllegalArgumentException("RFC2231Utils: Incomplete trailing escape (%) pattern");

                    String next = new String(bytes, 0, pos, cs); // new String(bytes, 0, pos, charset);
                    for (char cc : next.toCharArray()) {
                        // Additional validation: stop processing if CTLs encountered, though CTLs in "%HH%" is not
                        // disallowed explicitly by RFC
                        if (cc == 0x00 || cc < 0x20) {
                            throw new IllegalArgumentException("RFC2231Utils: Illegal decoded char");
                        }
                    }
                    sb.append(next);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "RFC2231Utils: Illegal hex characters in escape (%) pattern - " + e.getMessage());
                }
                needToChange = true;
                break;
            default:
                if (c < 0 || c > 0x7f || !isAttributeChar(c)) {
                    throw new IllegalArgumentException("RFC2231Utils: Illegal attribute-char");
                }
                sb.append(c);
                i++;
                break;
            }
        }

        return (needToChange ? sb.toString() : encodedValue);
    }

    private static String getJavaCharset(final String mimeCharset) {
        // good enough for standard values
        return mimeCharset;
    }

    /**
     * Tests if asterisk (*) at the end of parameter name to indicate, if it has charset and language information to decode the value.
     *
     * @param paramName The parameter, which is being checked.
     * @return {@code true}, if encoded as per RFC 2231, {@code false} otherwise
     */
    static boolean hasEncodedValue(final String paramName) {
        if (paramName != null) {
            return paramName.lastIndexOf('*') == paramName.length() - 1;
        }
        return false;
    }

    /**
     * If {@code paramName} has Asterisk (*) at the end, it will be stripped off, else the passed value will be returned.
     *
     * @param paramName The parameter, which is being inspected.
     * @return stripped {@code paramName} of Asterisk (*), if RFC2231 encoded
     */
    static String stripDelimiter(final String paramName) {
        if (hasEncodedValue(paramName)) {
            final var paramBuilder = new StringBuilder(paramName);
            paramBuilder.deleteCharAt(paramName.lastIndexOf('*'));
            return paramBuilder.toString();
        }
        return paramName;
    }

    /**
     * Private constructor so that no instances can be created. This class contains only static utility methods.
     */
    private RFC2231Utils() {
    }
}
