public class TimeoutException extends RuntimeException {

    public TimeoutException(){
        super();
    }

    /**
     * @param message
     * @param cause
     */
    public TimeoutException(String message, Throwable cause){
        super(message, cause);
    }

    /**
     * @param message
     */
    public TimeoutException(String message){
        super(message);
    }

    /**
     * @param cause
     */
    public TimeoutException(Throwable cause){
        super(cause);
    }

}