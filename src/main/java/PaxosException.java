public class PaxosException extends RuntimeException {

    public PaxosException(){
        super();
    }

    /**
     * @param message
     * @param cause
     */
    public PaxosException(String message, Throwable cause){
        super(message, cause);
    }

    /**
     * @param message
     */
    public PaxosException(String message){
        super(message);
    }

    /**
     * @param cause
     */
    public PaxosException(Throwable cause){
        super(cause);
    }

}