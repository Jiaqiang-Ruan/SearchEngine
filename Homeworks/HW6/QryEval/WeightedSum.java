import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;

public class WeightedSum {

    FloatMatrix idfWts;

    public WeightedSum() {
    }

    public FloatMatrix forward(FloatMatrix rel, FloatMatrix nonRel, FloatMatrix idfWts) {
        this.idfWts = idfWts;
        return new FloatMatrix(2, 1, rel.mul(idfWts).sum(), nonRel.mul(idfWts).sum());
    }

    public FloatMatrix backward(FloatMatrix delta) {
        FloatMatrix gMatrix = delta.mmul(this.idfWts.transpose());
        gMatrix = gMatrix.transpose();
        gMatrix.reshape(gMatrix.rows * gMatrix.columns, 1);
        return gMatrix;


    }
}