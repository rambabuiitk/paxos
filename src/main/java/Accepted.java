public class Accepted implements NAKAble {

    private Object  var;
    private Object  value;
    private Long    epoch;
    private boolean NAK;

    public static Accepted create(Long epoch, Object var, Object value) {
        return create(epoch, var, value, false);
    }

    public static Accepted create(Long epoch, Object var, Object value, boolean isNAK) {
        return new Accepted(epoch, var, value, isNAK);
    }

    public Accepted(Long epoch, Object var, Object value, boolean isNAK){
        this.epoch = epoch;
        this.var = var;
        this.value = value;
        this.NAK = isNAK;
    }

    public Object getVar() {
        return var;
    }

    public Object getValue() {
        return value;
    }

    public Long getEpoch() {
        return epoch;
    }

    @Override
    public boolean isNAK() {
        return NAK;
    }

}