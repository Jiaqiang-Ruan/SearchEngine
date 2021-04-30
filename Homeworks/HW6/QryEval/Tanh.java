import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;

public class Tanh extends Activation {

    public Tanh() {
        super();
    }

    public FloatMatrix forward(FloatMatrix x) {
        this.state = MatrixFunctions.tanh(x);
        return this.state;
    }

    public FloatMatrix derivative() {
        return this.state.mul(this.state).rsub(1.0F);

    }
}
