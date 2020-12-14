import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;

public class Sigmoid extends Activation {

    public Sigmoid() {
        super();
    }

    public FloatMatrix forward(FloatMatrix x) {
        this.state = MatrixFunctions.exp(x.neg()).add(1.0F).rdiv(1.0F);
        return this.state;
    }

    public FloatMatrix derivative() {
        return this.state.mul(this.state.rsub(1.0F));

    }
}