package de.wasessenwir.security;

/**
 * Gemeinsame HTTP-Sicherheitsheader für API-Antworten. CSP bleibt bewusst eng und mit dem
 * bestehenden React-/MUI-Frontend sowie der PWA kompatibel ({@code style-src} braucht
 * {@code 'unsafe-inline'} wegen Emotion/MUI).
 */
public final class SecurityHeaders {

    public static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; "
                    + "base-uri 'none'; "
                    + "frame-ancestors 'none'; "
                    + "form-action 'none'; "
                    + "object-src 'none'";

    public static final String REFERRER_POLICY = "strict-origin-when-cross-origin";

    public static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), "
                    + "magnetometer=(), microphone=(), payment=(), usb=()";

    public static final String FRONTEND_CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    + "base-uri 'self'; "
                    + "object-src 'none'; "
                    + "frame-ancestors 'none'; "
                    + "form-action 'self'; "
                    + "script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; "
                    + "font-src 'self'; "
                    + "connect-src 'self'; "
                    + "manifest-src 'self'; "
                    + "worker-src 'self'";

    private SecurityHeaders() {}
}
