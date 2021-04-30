import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;
import org.jblas.ranges.Range;
import org.jblas.ranges.RangeUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MLP {

    int num_layers;
    int input_size;
    int output_size;
    List<Activation> activations;
    float lr;
    FloatMatrix output;
    List<Linear> linear_layers;


    public MLP(int input_size, int output_size, int[] hiddens, ArrayList<Activation> activations, float lr, long seed) {
        this.num_layers = hiddens.length + 1;
        this.input_size = input_size;
        this.output_size = output_size;
        this.activations = activations;
        this.lr = lr;
        this.linear_layers = new ArrayList<>();
        // nifty one liner that converts integer arrays to int ArrayLists
        List<Integer> layer_sizes = Arrays.stream(hiddens).boxed().collect(Collectors.toList());
        layer_sizes.add(0, input_size);
        layer_sizes.add(output_size);

        for (int i = 0; i < layer_sizes.size() - 1; i++) {
            this.linear_layers.add(new Linear(layer_sizes.get(i), layer_sizes.get(i + 1), seed * (2 + i)));
        }

        this.output = null;
        if (this.linear_layers.size() != this.activations.size()) {
            throw new AssertionError(String.format("Linear layer size (=%d) != Activations size (=%d)", this.linear_layers.size(), this.activations.size()));
        }

    }

    public FloatMatrix forward(FloatMatrix x) {


        for (int i = 0; i < this.linear_layers.size(); i++) {
            x = this.linear_layers.get(i).forward(x);
            x = this.activations.get(i).forward(x);
        }

        this.output = x;


        return this.output;
    }

    public void zero_grad() {
        for (Linear layer : this.linear_layers) {
            layer.dW.fill(0.0F);
            layer.db.fill(0.0F);
        }
    }

    public void sgdStep() {
        for (Linear layer : this.linear_layers) {
            layer.W.subi(layer.dW.mul(this.lr));
            layer.b.subi(layer.db.mul(this.lr));
        }
    }

    public void adagradStep() {
        float eps = 1e-7F;
        for (Linear layer : this.linear_layers) {
            // TODO: Need to do this properly
            layer.sqW.addi(MatrixFunctions.pow(layer.dW, 2));
            layer.sqb.addi(MatrixFunctions.pow(layer.db, 2));
            FloatMatrix divW = layer.dW.mul(this.lr).div(MatrixFunctions.sqrt(layer.sqW.add(eps)));
            FloatMatrix divb = layer.db.mul(this.lr).div(MatrixFunctions.sqrt(layer.sqb.add(eps)));
            layer.W.subi(divW);
            layer.b.subi(divb);
        }
    }

    public void backward(FloatMatrix delta) {

        for (int i = this.linear_layers.size() - 1; i >= 0; i--) {
            FloatMatrix new_delta = this.activations.get(i).derivative().mul(delta);
            delta = this.linear_layers.get(i).backward(new_delta);
        }

    }

    public float error(FloatMatrix labels) {
        int[] predicted = this.output.rowArgmaxs();
        int[] actual = labels.rowArgmaxs();
        float num_errors = 0;
        for (int i = 0; i < predicted.length; i++) {
            if (predicted[i] != actual[i])
                num_errors += 1;
        }
        return num_errors;
    }

    public void save(String filename) throws IOException {
        List<String> lines = new ArrayList<>();
        Path file = Paths.get(filename);
        int i = 1;
        for (Linear l : this.linear_layers) {
            lines.add("W #" + String.valueOf(i) + " " + l.W.toString("%.15f"));
            lines.add("b #" + String.valueOf(i) + " " + l.b.toString("%.15f"));
            i++;
        }
        Files.write(file, lines,
                Files.exists(file) ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.CREATE);
    }

    public void load(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line = reader.readLine();
        while (line != null) {
            String[] splits = line.split(" ", 3);
            String layerType = splits[0];
            int layerNum = Integer.parseInt(splits[1].split("#")[1]) - 1;
            String floatList = splits[2];
            floatList = floatList.substring(1, floatList.length() - 1) + ";";
            String[] floats = floatList.split(" ");
            int numColumns = -1;
            if (layerType.equalsIgnoreCase("w"))
                numColumns = this.linear_layers.get(layerNum).W.columns;
            else if (layerType.equalsIgnoreCase("b"))
                numColumns = this.linear_layers.get(layerNum).b.columns;
            int index = 0;
            int indexRows = 0;
            float[] tempRow = new float[numColumns];
            for (String num : floats) {
                String cleanedFloat = num.substring(0, num.length() - 1);
                if (num.charAt(num.length() - 1) == ',')
                    tempRow[index] = Float.parseFloat(cleanedFloat);
                else if (num.charAt(num.length() - 1) == ';') {
                    tempRow[index] = Float.parseFloat(cleanedFloat);
                    if (layerType.equalsIgnoreCase("w"))
                        this.linear_layers.get(layerNum).W.putRow(indexRows, new FloatMatrix(tempRow).transpose());
                    else if (layerType.equalsIgnoreCase("b"))
                        this.linear_layers.get(layerNum).b.putRow(indexRows, new FloatMatrix(tempRow).transpose());
                    indexRows += 1;
                    index = -1;
                }
                index += 1;
            }
            line = reader.readLine();
        }
    }

    public static void main(String args[]) throws IOException {
        int num_epochs = 1000;
        int batch_size = 128;

        double[] trainingLosses = new double[num_epochs];
        double[] trainingErrors = new double[num_epochs];
        double[] validationLosses = new double[num_epochs];
        double[] validationErrors = new double[num_epochs];

        org.jblas.util.Random.seed(11642);


        FloatMatrix trainX = FloatMatrix.randn(1000, 784);
        FloatMatrix trainY = FloatMatrix.randn(1000, 10);
//
        FloatMatrix valX = FloatMatrix.randn(100, 784);
        FloatMatrix valY = FloatMatrix.randn(100, 10);


        ArrayList<Activation> activations = new ArrayList<>();
        activations.add(new Sigmoid());
        activations.add(new Sigmoid());
        activations.add(new Sigmoid());
        activations.add(new Identity());


        //MLP mlp = new MLP(784, 10, new int[]{512, 256, 128}, activations, new SoftmaxCrossEntropy(), 0.01F);
        MLP mlp = new MLP(784, 10, new int[]{32, 32, 32}, activations, 0.01F, 11642);

        List<Integer> indices = IntStream.range(0, trainX.rows).boxed().collect(Collectors.toList());

        Criterion criterion = new SoftmaxCrossEntropy();


        for (int i = 0; i < num_epochs; i++) {
            long startTime = System.nanoTime();
            Collections.shuffle(indices, new Random(11642));
            int[] train_indices = indices.stream().mapToInt(j -> j).toArray();
            ArrayList<Float> loss = new ArrayList<>();
            ArrayList<Float> errors = new ArrayList<>();

            for (int b = 0; b < trainX.rows; b += batch_size) {
                mlp.zero_grad();
                int[] batch_indices = Arrays.copyOfRange(train_indices, b, Math.min(b + batch_size, trainX.rows));
                FloatMatrix batch_inputs = trainX.getRows(batch_indices);
                FloatMatrix batch_labels = trainY.getRows(batch_indices);
                FloatMatrix outputs = mlp.forward(batch_inputs);
                FloatMatrix lossOutputs = criterion.forward(outputs, batch_labels);
                FloatMatrix delta = criterion.derivative();
                loss.add(lossOutputs.sum() / batch_size);
                errors.add(mlp.error(batch_labels) / batch_size);
                mlp.backward(delta);
                mlp.sgdStep();
            }
            trainingLosses[i] = loss.stream().mapToDouble(val -> val).average().orElse(0.0);
            trainingErrors[i] = errors.stream().mapToDouble(val -> val).average().orElse(0.0);

            loss = new ArrayList<>();
            errors = new ArrayList<>();

            for (int b = 0; b < valX.rows; b += batch_size) {
                Range batch_indices = RangeUtils.interval(b, Math.min(b + batch_size, valX.rows));
                FloatMatrix batch_inputs = valX.getRows(batch_indices);
                FloatMatrix batch_labels = valY.getRows(batch_indices);
                FloatMatrix outputs = mlp.forward(batch_inputs);
                FloatMatrix lossOutputs = criterion.forward(outputs, batch_labels);
                mlp.forward(batch_inputs);
                loss.add(lossOutputs.sum() / batch_size);
                errors.add(mlp.error(batch_labels) / batch_size);
            }
            validationLosses[i] = loss.stream().mapToDouble(val -> val).average().orElse(0.0);
            validationErrors[i] = errors.stream().mapToDouble(val -> val).average().orElse(0.0);

            System.out.println("Epoch " + i);
            System.out.println("Training Loss: " + trainingLosses[i]);
            System.out.println("Training Error: " + trainingErrors[i]);
            System.out.println("Validation Loss: " + validationLosses[i]);
            System.out.println("Validation Error: " + validationErrors[i]);
            System.out.println("Time taken for an epoch: " + (System.nanoTime() - startTime) / 1000000000 + "s");
            System.out.println("******************************************");

        }
    }
}
