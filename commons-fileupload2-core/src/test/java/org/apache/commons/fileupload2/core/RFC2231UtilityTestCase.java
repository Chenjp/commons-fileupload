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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Test;

/**
 * The expected characters are encoded in UTF16, while the actual characters may be encoded in UTF-8/ISO-8859-1
 *
 * RFC 5987 recommends to support both UTF-8 and ISO 8859-1. Test values are taken from https://tools.ietf.org/html/rfc5987#section-3.2.2
 */
public final class RFC2231UtilityTestCase {

    private static void assertEncoded(final String expected, final String encoded) throws Exception {
        assertEquals(expected, RFC2231Utils.decodeText(encoded));
    }

    @Test
    void testDecodeInvalidEncoding() throws Exception {
        assertThrows(UnsupportedEncodingException.class, () -> RFC2231Utils.decodeText("abc'en'hello"));
    }

    @Test
    void testDecodeIso88591() throws Exception {
        assertEncoded("\u00A3 rate", "iso-8859-1'en'%A3%20rate"); // "£ rate"
    }

    @Test
    void testDecodeUtf8() throws Exception {
        assertEncoded("\u00a3 \u0061\u006e\u0064 \u20ac \u0072\u0061\u0074\u0065\u0073", "UTF-8''%C2%A3%20and%20%E2%82%AC%20rates"); // "£ and € rates"
    }

    @Test
    void testDecodeUtf8_invalid_input() throws Exception {
    	assertThrows(IllegalArgumentException.class, ()->RFC2231Utils.decodeText("UTF-8''%c2%a3%20and%20%e2%82%ac%20rates"));
    	// lowercase a-f is not supported
    }

    @Test
    void testDecodeUtf8_invalid_input2() throws Exception {
        String source = new String(
                new byte[] { (byte) 0xe9, (byte) 0x98, (byte) 0xae, (byte) 0xe9, (byte) 0x98, (byte) 0xae }, "UTF-8");
        assertThrows(IllegalArgumentException.class, () -> RFC2231Utils.decodeText("UTF-8''" + source));
    }

    @Test
    void testDecodeUtf16() throws Exception {
        String utf16Source = "\u8a2e.txt";
        byte[] bb = utf16Source.getBytes("utf-16");
        StringBuilder buf = new StringBuilder();
        for(byte b:bb) {
            buf.append(String.format("%%%02X", b));
        }
        assertEquals(utf16Source, RFC2231Utils.decodeText("UTF-16''" + buf.toString()));
    }

    @Test
    void testHasEncodedValue() {
        final var nameWithAsteriskAtEnd = "paramname*";
        assertTrue(RFC2231Utils.hasEncodedValue(nameWithAsteriskAtEnd));

        final var nameWithAsteriskNotAtEnd = "param*name";
        assertFalse(RFC2231Utils.hasEncodedValue(nameWithAsteriskNotAtEnd));

        final var nameWithoutAsterisk = "paramname";
        assertFalse(RFC2231Utils.hasEncodedValue(nameWithoutAsterisk));
    }

    @Test
    void testNoNeedToDecode() throws Exception {
        assertEncoded("abc", "utf-8''abc");
        assertThrows(IllegalArgumentException.class,()->RFC2231Utils.decodeText("abc"));
    }

    @Test
    void testStripDelimiter() {
        final var nameWithAsteriskAtEnd = "paramname*";
        assertEquals("paramname", RFC2231Utils.stripDelimiter(nameWithAsteriskAtEnd));

        final var nameWithAsteriskNotAtEnd = "param*name";
        assertEquals("param*name", RFC2231Utils.stripDelimiter(nameWithAsteriskNotAtEnd));

        final var nameWithTwoAsterisks = "param*name*";
        assertEquals("param*name", RFC2231Utils.stripDelimiter(nameWithTwoAsterisks));

        final var nameWithoutAsterisk = "paramname";
        assertEquals("paramname", RFC2231Utils.stripDelimiter(nameWithoutAsterisk));
    }

    @Test
    void testUtf8_multibytes() throws Exception {
        String filename = new String(new byte[] { (byte) 0xe8, (byte) 0xa8, (byte) 0xae, (byte) 0xe8, (byte) 0xa8,
                (byte) 0xae, (byte) 0xe8, (byte) 0xa8, (byte) 0xaf, (byte) 0xe8, (byte) 0xa9, (byte) 0xb2, (byte) 0xe8,
                (byte) 0xa9, (byte) 0xa3, (byte) 'e', (byte) '.', (byte) 'j', (byte) 's', (byte) 0xe8, (byte) 0xa9,
                (byte) 0xb0 }, "utf-8");
        assertThrows(IllegalArgumentException.class, ()->RFC2231Utils.decodeText("UTF-8''" + filename));
    }
    
    @Test
    void testInvalidEncodedValue() throws Exception {
        String filename = "\u8a2e\u8a2e\u8a2f\u8a72\u8a63e.js\u8a70";
        assertThrows(IllegalArgumentException.class, () -> RFC2231Utils.decodeText("UTF-8''" + filename));
        assertThrows(IllegalArgumentException.class, () -> RFC2231Utils.decodeText("UTF-8''" + filename));
    }
}
