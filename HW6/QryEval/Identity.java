import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;

public class Identity extends Activation {

    public Identity() {
        super();
    }

    public FloatMatrix forward(FloatMatrix x) {
        this.state = x;
        return this.state;
    }

    public FloatMatrix derivative() {
        return FloatMatrix.ones(this.state.rows, this.state.columns);

    }
}