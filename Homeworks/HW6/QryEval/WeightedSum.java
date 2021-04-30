import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;
import org.jblas.SimpleBlas;

public class WeightedSum {

    FloatMatrix idfWts;

    public WeightedSum() {
    }

    public FloatMatrix forward(FloatMatrix rel, FloatMatrix nonRel, FloatMatrix idfWts) {
        this.idfWts = idfWts;
        float newRelScores = rel.mul(this.idfWts).sum();
        float newNonRelScores = nonRel.mul(this.idfWts).sum();
        FloatMatrix scores = new FloatMatrix(new float[]{newRelScores, newNonRelScores});
        return scores;
    }

    public FloatMatrix backward(FloatMatrix delta) {
        FloatMatrix gMatrix = delta.mmul(this.idfWts.transpose());
        gMatrix = gMatrix.transpose();
        gMatrix.reshape(gMatrix.rows * gMatrix.columns, 1);
        return gMatrix;


    }
}