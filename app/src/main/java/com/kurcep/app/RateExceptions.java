package com.kurcep.app;

class RateException extends Exception {
    RateException(String message) {
        super(message);
    }

    RateException(String message, Throwable cause) {
        super(message, cause);
    }
}

final class TransientRateException extends RateException {
    TransientRateException(String message, Throwable cause) {
        super(message, cause);
    }
}

final class RateHttpException extends RateException {
    final int status;

    RateHttpException(int status, String address) {
        super("Sunucu hatası: " + status + " (" + address + ")");
        this.status = status;
    }

    boolean isRetryable() {
        return status == 408 || status == 429 || status >= 500;
    }
}

class PermanentRateException extends RateException {
    PermanentRateException(String message) {
        super(message);
    }

    PermanentRateException(String message, Throwable cause) {
        super(message, cause);
    }
}

final class InvalidRateException extends PermanentRateException {
    InvalidRateException(String message) {
        super(message);
    }

    InvalidRateException(String message, Throwable cause) {
        super(message, cause);
    }
}

final class ResponseTooLargeException extends PermanentRateException {
    ResponseTooLargeException(int maxBytes) {
        super("HTTP yanıtı izin verilen boyutu aşıyor: " + maxBytes + " byte");
    }
}
