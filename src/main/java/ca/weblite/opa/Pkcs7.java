package ca.weblite.opa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal PKCS#7 (CMS) SignedData builder and parser using raw DER encoding.
 * Follows RFC 2315 / RFC 5652 to produce the same format as JAR signing.
 *
 * <p>Package-private — used only by {@link OpaSigner} and {@link OpaVerifier}.</p>
 */
class Pkcs7 {

    // OIDs
    private static final int[] OID_SIGNED_DATA = {1, 2, 840, 113549, 1, 7, 2};
    private static final int[] OID_DATA = {1, 2, 840, 113549, 1, 7, 1};
    private static final int[] OID_SHA256 = {2, 16, 840, 1, 101, 3, 4, 2, 1};
    private static final int[] OID_SHA256_WITH_RSA = {1, 2, 840, 113549, 1, 1, 11};
    private static final int[] OID_SHA256_WITH_ECDSA = {1, 2, 840, 10045, 4, 3, 2};
    private static final int[] OID_SHA256_WITH_DSA = {2, 16, 840, 1, 101, 3, 4, 3, 2};

    /**
     * Build a PKCS#7 SignedData DER block.
     *
     * @param signatureAlgorithm e.g. "SHA256withRSA"
     * @param signatureBytes     raw signature over SIGNATURE.SF
     * @param certChain          certificate chain (first = signer)
     * @return DER-encoded PKCS#7 ContentInfo wrapping SignedData
     */
    static byte[] buildSignedData(String signatureAlgorithm, byte[] signatureBytes,
                                  Certificate[] certChain)
            throws CertificateEncodingException {
        X509Certificate signerCert = (X509Certificate) certChain[0];

        // --- digestAlgorithms SET ---
        byte[] digestAlgId = derAlgorithmIdentifier(OID_SHA256);
        byte[] digestAlgorithms = derSet(digestAlgId);

        // --- encapContentInfo (external data, no content) ---
        byte[] encapContentInfo = derSequence(derOID(OID_DATA));

        // --- certificates [0] IMPLICIT ---
        ByteArrayOutputStream certsContent = new ByteArrayOutputStream();
        for (Certificate cert : certChain) {
            byte[] certDer = cert.getEncoded();
            write(certsContent, certDer);
        }
        byte[] certificates = derTagExplicit(0xA0, certsContent.toByteArray());

        // --- signerInfos SET ---
        byte[] signerInfo = buildSignerInfo(signatureAlgorithm, signatureBytes, signerCert);
        byte[] signerInfos = derSet(signerInfo);

        // --- SignedData SEQUENCE ---
        byte[] signedData = derSequence(concat(
                derInteger(BigInteger.ONE),    // version
                digestAlgorithms,
                encapContentInfo,
                certificates,
                signerInfos
        ));

        // --- ContentInfo SEQUENCE ---
        return derSequence(concat(
                derOID(OID_SIGNED_DATA),
                derTagExplicit(0xA0, signedData)
        ));
    }

    private static byte[] buildSignerInfo(String signatureAlgorithm, byte[] signatureBytes,
                                          X509Certificate signerCert) {
        // issuerAndSerialNumber
        byte[] issuerDer = signerCert.getIssuerX500Principal().getEncoded();
        byte[] serialDer = derInteger(signerCert.getSerialNumber());
        byte[] issuerAndSerial = derSequence(concat(issuerDer, serialDer));

        // digestAlgorithm
        byte[] digestAlgId = derAlgorithmIdentifier(OID_SHA256);

        // signatureAlgorithm
        byte[] sigAlgId = derAlgorithmIdentifier(signatureAlgorithmOID(signatureAlgorithm));

        // signature OCTET STRING
        byte[] sigOctet = derOctetString(signatureBytes);

        return derSequence(concat(
                derInteger(BigInteger.ONE),    // version
                issuerAndSerial,
                digestAlgId,
                sigAlgId,
                sigOctet
        ));
    }

    // --- PKCS#7 parsing ---

    /**
     * Parsed result from a PKCS#7 SignedData block.
     */
    static class ParsedSignedData {
        String signatureAlgorithm;
        byte[] signature;
        Certificate[] certificates;
        PublicKey signerPublicKey;
    }

    /**
     * Parse a DER-encoded PKCS#7 ContentInfo containing SignedData.
     */
    static ParsedSignedData parseSignedData(byte[] data) throws Exception {
        DerParser parser = new DerParser(data);

        // ContentInfo SEQUENCE
        DerParser contentInfo = parser.enterSequence();
        contentInfo.skipElement(); // contentType OID

        // [0] EXPLICIT -> SignedData
        DerParser explicit0 = contentInfo.enterExplicit(0xA0);
        DerParser signedData = explicit0.enterSequence();

        signedData.skipElement(); // version
        signedData.skipElement(); // digestAlgorithms
        signedData.skipElement(); // encapContentInfo

        // certificates [0] IMPLICIT (optional)
        List<Certificate> certs = new ArrayList<>();
        DerParser.Element next = signedData.peekElement();
        if (next != null && next.tag == (byte) 0xA0) {
            byte[] certsBytes = signedData.readRawElement();
            // The content inside [0] is a sequence of DER-encoded certificates
            // We need to strip the outer tag+length and parse each cert
            DerParser certsParser = new DerParser(certsBytes);
            byte[] innerContent = certsParser.readContentOfConstructed();
            parseCertificates(innerContent, certs);
        }

        // signerInfos SET
        DerParser signerInfosSet = signedData.enterSet();
        DerParser signerInfo = signerInfosSet.enterSequence();

        signerInfo.skipElement(); // version
        signerInfo.skipElement(); // issuerAndSerialNumber

        signerInfo.skipElement(); // digestAlgorithm

        // signatureAlgorithm
        byte[] sigAlgBytes = signerInfo.readRawElement();
        String sigAlg = parseAlgorithmIdentifier(sigAlgBytes);

        // signature OCTET STRING
        byte[] signature = signerInfo.readOctetString();

        ParsedSignedData result = new ParsedSignedData();
        result.signatureAlgorithm = sigAlg;
        result.signature = signature;
        result.certificates = certs.toArray(new Certificate[0]);
        if (!certs.isEmpty()) {
            result.signerPublicKey = certs.get(0).getPublicKey();
        }
        return result;
    }

    private static void parseCertificates(byte[] data, List<Certificate> certs) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        int offset = 0;
        while (offset < data.length) {
            // Each certificate is a SEQUENCE — read tag+length to find its total size
            if (offset >= data.length) break;
            int[] tagAndLen = readTagAndLength(data, offset);
            int totalLen = tagAndLen[2]; // header + content length
            byte[] certBytes = new byte[totalLen];
            System.arraycopy(data, offset, certBytes, 0, totalLen);
            certs.add(cf.generateCertificate(new ByteArrayInputStream(certBytes)));
            offset += totalLen;
        }
    }

    private static String parseAlgorithmIdentifier(byte[] data) throws OpaSignatureException {
        // SEQUENCE { OID, optional params }
        try {
            DerParser seq = new DerParser(data);
            DerParser inner = seq.enterSequence();
            byte[] oidBytes = inner.readRawElement();
            int[] oid = decodeOID(oidBytes);
            return oidToAlgorithmName(oid);
        } catch (Exception e) {
            throw new OpaSignatureException("Cannot parse algorithm identifier", e);
        }
    }

    // --- DER encoding helpers ---

    static byte[] derSequence(byte[] content) {
        return derTag(0x30, content);
    }

    static byte[] derSet(byte[] content) {
        return derTag(0x31, content);
    }

    static byte[] derInteger(BigInteger value) {
        return derTag(0x02, value.toByteArray());
    }

    static byte[] derOctetString(byte[] content) {
        return derTag(0x04, content);
    }

    static byte[] derOID(int[] oid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oid[0] * 40 + oid[1]);
        for (int i = 2; i < oid.length; i++) {
            encodeOIDComponent(out, oid[i]);
        }
        return derTag(0x06, out.toByteArray());
    }

    static byte[] derAlgorithmIdentifier(int[] oid) {
        return derSequence(concat(derOID(oid), new byte[]{0x05, 0x00}));
    }

    static byte[] derTagExplicit(int tag, byte[] content) {
        return derTag(tag, content);
    }

    private static byte[] derTag(int tag, byte[] content) {
        byte[] header = new byte[]{(byte) tag};
        byte[] length = derLength(content.length);
        return concat(header, length, content);
    }

    private static byte[] derLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        } else if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else if (length < 65536) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        } else {
            return new byte[]{(byte) 0x83, (byte) (length >> 16),
                    (byte) (length >> 8), (byte) length};
        }
    }

    private static void encodeOIDComponent(ByteArrayOutputStream out, int value) {
        if (value < 128) {
            out.write(value);
            return;
        }
        byte[] bytes = new byte[5];
        int pos = 4;
        bytes[pos--] = (byte) (value & 0x7F);
        value >>= 7;
        while (value > 0) {
            bytes[pos--] = (byte) (0x80 | (value & 0x7F));
            value >>= 7;
        }
        out.write(bytes, pos + 1, 4 - pos);
    }

    private static int[] signatureAlgorithmOID(String alg) {
        switch (alg) {
            case "SHA256withRSA": return OID_SHA256_WITH_RSA;
            case "SHA256withECDSA": return OID_SHA256_WITH_ECDSA;
            case "SHA256withDSA": return OID_SHA256_WITH_DSA;
            default: throw new IllegalArgumentException("Unsupported signature algorithm: " + alg);
        }
    }

    private static String oidToAlgorithmName(int[] oid) throws OpaSignatureException {
        if (oidEquals(oid, OID_SHA256_WITH_RSA)) return "SHA256withRSA";
        if (oidEquals(oid, OID_SHA256_WITH_ECDSA)) return "SHA256withECDSA";
        if (oidEquals(oid, OID_SHA256_WITH_DSA)) return "SHA256withDSA";
        throw new OpaSignatureException("Unknown signature algorithm OID");
    }

    private static boolean oidEquals(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static int[] decodeOID(byte[] element) {
        // element is tag(06) + length + content
        int offset = 0;
        offset++; // skip tag
        int[] lenResult = decodeDerLength(element, offset);
        int contentLen = lenResult[0];
        offset = lenResult[1];

        byte[] content = new byte[contentLen];
        System.arraycopy(element, offset, content, 0, contentLen);

        List<Integer> components = new ArrayList<>();
        components.add(content[0] / 40);
        components.add(content[0] % 40);
        int value = 0;
        for (int i = 1; i < content.length; i++) {
            value = (value << 7) | (content[i] & 0x7F);
            if ((content[i] & 0x80) == 0) {
                components.add(value);
                value = 0;
            }
        }
        return components.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Reads tag byte and length from DER data starting at given offset.
     * Returns [tag, headerLength, totalLength(header+content)].
     */
    static int[] readTagAndLength(byte[] data, int offset) {
        int tag = data[offset] & 0xFF;
        int[] lenResult = decodeDerLength(data, offset + 1);
        int contentLen = lenResult[0];
        int headerLen = lenResult[1] - offset;
        return new int[]{tag, headerLen, headerLen + contentLen};
    }

    private static int[] decodeDerLength(byte[] data, int offset) {
        int first = data[offset] & 0xFF;
        if (first < 128) {
            return new int[]{first, offset + 1};
        }
        int numBytes = first & 0x7F;
        int length = 0;
        for (int i = 0; i < numBytes; i++) {
            length = (length << 8) | (data[offset + 1 + i] & 0xFF);
        }
        return new int[]{length, offset + 1 + numBytes};
    }

    static byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private static void write(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    // --- Minimal DER stream parser ---

    static class DerParser {
        private final byte[] data;
        private int pos;

        DerParser(byte[] data) {
            this.data = data;
            this.pos = 0;
        }

        DerParser enterSequence() throws OpaSignatureException {
            return enterConstructed(0x30);
        }

        DerParser enterSet() throws OpaSignatureException {
            return enterConstructed(0x31);
        }

        DerParser enterExplicit(int expectedTag) throws OpaSignatureException {
            return enterConstructed(expectedTag);
        }

        private DerParser enterConstructed(int expectedTag) throws OpaSignatureException {
            if (pos >= data.length) {
                throw new OpaSignatureException("Unexpected end of DER data");
            }
            int tag = data[pos] & 0xFF;
            if (tag != expectedTag) {
                throw new OpaSignatureException(
                        String.format("Expected tag 0x%02X but got 0x%02X at offset %d",
                                expectedTag, tag, pos));
            }
            pos++;
            int[] lenResult = decodeDerLength(data, pos);
            int contentLen = lenResult[0];
            int contentStart = lenResult[1];
            byte[] content = new byte[contentLen];
            System.arraycopy(data, contentStart, content, 0, contentLen);
            pos = contentStart + contentLen;
            return new DerParser(content);
        }

        byte[] readContentOfConstructed() throws OpaSignatureException {
            if (pos >= data.length) {
                throw new OpaSignatureException("Unexpected end of DER data");
            }
            pos++; // skip tag
            int[] lenResult = decodeDerLength(data, pos);
            int contentLen = lenResult[0];
            int contentStart = lenResult[1];
            byte[] content = new byte[contentLen];
            System.arraycopy(data, contentStart, content, 0, contentLen);
            pos = contentStart + contentLen;
            return content;
        }

        void skipElement() throws OpaSignatureException {
            if (pos >= data.length) {
                throw new OpaSignatureException("Unexpected end of DER data");
            }
            pos++; // skip tag
            int[] lenResult = decodeDerLength(data, pos);
            pos = lenResult[1] + lenResult[0];
        }

        byte[] readRawElement() throws OpaSignatureException {
            if (pos >= data.length) {
                throw new OpaSignatureException("Unexpected end of DER data");
            }
            int start = pos;
            pos++; // skip tag
            int[] lenResult = decodeDerLength(data, pos);
            pos = lenResult[1] + lenResult[0];
            byte[] element = new byte[pos - start];
            System.arraycopy(data, start, element, 0, element.length);
            return element;
        }

        byte[] readOctetString() throws OpaSignatureException {
            if (pos >= data.length) {
                throw new OpaSignatureException("Unexpected end of DER data");
            }
            int tag = data[pos] & 0xFF;
            if (tag != 0x04) {
                throw new OpaSignatureException(
                        String.format("Expected OCTET STRING (0x04) but got 0x%02X", tag));
            }
            pos++;
            int[] lenResult = decodeDerLength(data, pos);
            int contentLen = lenResult[0];
            int contentStart = lenResult[1];
            byte[] content = new byte[contentLen];
            System.arraycopy(data, contentStart, content, 0, contentLen);
            pos = contentStart + contentLen;
            return content;
        }

        Element peekElement() {
            if (pos >= data.length) return null;
            return new Element(data[pos]);
        }

        boolean hasMore() {
            return pos < data.length;
        }

        static class Element {
            final byte tag;
            Element(byte tag) { this.tag = tag; }
        }
    }
}
