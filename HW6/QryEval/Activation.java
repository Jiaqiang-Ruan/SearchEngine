import org.jblas.FloatMatrix;


public abstract class Activation {
    FloatMatrix state;

    public Activation() {
        this.state = null;
    }

    public abstract FloatMatrix forward(FloatMatrix x);

    public abstract FloatMatrix derivative();

}