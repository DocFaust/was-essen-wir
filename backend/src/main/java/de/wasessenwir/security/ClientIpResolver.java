package de.wasessenwir.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Ermittelt die Client-IP für den AttemptLimiter. Ohne
 * {@code app.security.forward-client-ip} werden ausschließlich
 * {@link HttpServletRequest#getRemoteAddr()} und keine Forwarded-Header genutzt.
 * Mit aktivierter Weiterleitung werden {@code X-Forwarded-For} / {@code X-Real-IP}
 * nur ausgewertet, wenn die unmittelbare Peer-Adresse in
 * {@code app.security.trusted-proxies} liegt.
 */
@Component
public class ClientIpResolver {

    private final boolean forwardClientIp;
    private final List<Cidr> trustedProxies;

    public ClientIpResolver(SecurityProperties properties) {
        this.forwardClientIp = properties.forwardClientIp();
        this.trustedProxies = parseCidrs(properties.trustedProxyCidrs());
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!forwardClientIp || !isTrustedProxy(remote)) {
            return remote;
        }
        String forwarded = firstHop(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = firstHop(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return remote;
    }

    boolean isTrustedProxy(String address) {
        if (trustedProxies.isEmpty()) {
            return false;
        }
        byte[] bytes;
        try {
            bytes = InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
        for (Cidr cidr : trustedProxies) {
            if (cidr.contains(bytes)) {
                return true;
            }
        }
        return false;
    }

    static String firstHop(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String part : header.split(",")) {
            String candidate = normalize(part);
            if (!"unknown".equals(candidate) && looksLikeIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return AttemptLimiter.normalizeKey(value == null ? "" : value.trim());
    }

    private static boolean looksLikeIp(String value) {
        try {
            InetAddress.getByName(value);
            return value.indexOf(':') >= 0 || value.indexOf('.') >= 0;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static List<Cidr> parseCidrs(List<String> values) {
        List<Cidr> cidrs = new ArrayList<>();
        for (String value : values) {
            cidrs.add(Cidr.parse(value));
        }
        return List.copyOf(cidrs);
    }

    record Cidr(byte[] network, int prefixLength) {

        static Cidr parse(String value) {
            String trimmed = value.trim();
            int slash = trimmed.indexOf('/');
            String addressPart = slash < 0 ? trimmed : trimmed.substring(0, slash);
            byte[] address;
            try {
                address = InetAddress.getByName(addressPart).getAddress();
            } catch (UnknownHostException exception) {
                throw new IllegalStateException("Ungültige trusted-proxy-Adresse: " + value, exception);
            }
            int maxPrefix = address.length * 8;
            int prefix = slash < 0 ? maxPrefix : Integer.parseInt(trimmed.substring(slash + 1).trim());
            if (prefix < 0 || prefix > maxPrefix) {
                throw new IllegalStateException("Ungültiges trusted-proxy-Präfix: " + value);
            }
            return new Cidr(mask(address, prefix), prefix);
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            byte[] masked = mask(address, prefixLength);
            for (int index = 0; index < network.length; index++) {
                if (masked[index] != network[index]) {
                    return false;
                }
            }
            return true;
        }

        private static byte[] mask(byte[] address, int prefixLength) {
            byte[] copy = address.clone();
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = fullBytes + (remainingBits == 0 ? 0 : 1); index < copy.length; index++) {
                copy[index] = 0;
            }
            if (remainingBits != 0 && fullBytes < copy.length) {
                int mask = 0xFF << (8 - remainingBits);
                copy[fullBytes] = (byte) (copy[fullBytes] & mask);
            }
            return copy;
        }
    }
}
