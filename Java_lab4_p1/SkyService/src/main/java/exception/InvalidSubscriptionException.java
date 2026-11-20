package exception;


import lombok.experimental.StandardException;

@StandardException
public class InvalidSubscriptionException extends RuntimeException {
}

// Непроверяемое исключение (RuntimeException)
//public class InvalidSubscriptionException extends RuntimeException {
//    public InvalidSubscriptionException(String message) {
//        super(message);
//    }
//}

