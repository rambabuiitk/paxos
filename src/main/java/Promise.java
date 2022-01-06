public class Promise implements NAKAble {

    private Long    preEpoch;
    private Object  var;
    private Object  value;
    private boolean NAK;

    public Promise(Long preEpoch, Object var, Object value, boolean isNAK){
        this.preEpoch = preEpoch;
        this.var = var;
        this.value = value;
        this.NAK = isNAK;
    }

    public static Promise create(Long preEpoch, Object var, Object value) {
        return create(preEpoch, var, value, false);
    }

    public static Promise create(Long preEpoch, Object var, Object value, boolean isNAK) {
        return new Promise(preEpoch, var, value, isNAK);
    }

    public Long getPreEpoch() {
        return preEpoch;
    }

    public Object getVar() {
        return var;
    }

    public Object getValue() {
        return value;
    }

    /**
     * Whether it is a negative acknowledge, it means that the Promise is negative, and the epoch submitted by the Proposer is too low     *
     * @return
     */
    @Override
    public boolean isNAK() {
        return NAK;
    }

}
