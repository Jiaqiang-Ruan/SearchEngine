import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;
import org.jblas.SimpleBlas;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RetrievalModelDRMM {
    MLP mlp;
    WeightedSum wtdSum;


    RetrievalModelDRMM(Map<String, String> params) {
        try {
            IdxWordvec.open(params.get("drmm:word2vecPath"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Read in Scorelist and RelevanceList
        List<String> trainQids = new ArrayList<>();
        Map<String, ScoreList> trainScoreLists = new HashMap<>();
        Map<String, String> trainQueries = new HashMap<>();
        List<String> testQids = new ArrayList<>();
        Map<String, ScoreList> testScoreLists = new HashMap<>();
        Map<String, String> testQueries = new HashMap<>();
        Map<String, Map<Integer, Integer>> trainRelLists = new HashMap<>();

        prepareTrainScoreLists(params, trainScoreLists, trainQueries, trainQids);
        prepareTestScoreList(params, testScoreLists, testQueries, testQids);
        prepareTrainRelLists(params, trainRelLists);



        List<FloatMatrix> trainRel = new ArrayList<>();
        List<FloatMatrix> trainNonRel = new ArrayList<>();
        List<FloatMatrix> trainIdfWts = new ArrayList<>();

        // Sample trainX
        sample(params, trainQids, trainQueries, trainScoreLists, trainRelLists,
                trainRel, trainNonRel, trainIdfWts);

        // train
        System.err.println("start training\n");
        train(params, trainRel, trainNonRel, trainIdfWts);

       try {
           this.mlp.save(params.get("drmm:mlpWeightFile"));
       } catch (Exception e) {
           e.printStackTrace();
       }

        // Create testX
        Map<String, Map<Integer, FloatMatrix>> testRepresent = new HashMap<>();
        Map<String, Map<Integer, FloatMatrix>> testIdfWts = new HashMap<>();
        prepareTest(params, testQids, testQueries, testScoreLists,
                testRepresent, testIdfWts);

        // test
        Map<String, ScoreList> testOutput = new HashMap<>();
        test(testRepresent, testIdfWts, testOutput);

        // print
        printResult(params, testOutput);
    }

    private void printResult(Map<String, String> params,
                        Map<String, ScoreList> testOutput) {
        try {
            int number = Integer.parseInt(params.get("trecEvalOutputLength"));
            BufferedWriter output = new BufferedWriter(
                    new FileWriter(params.get("trecEvalOutputPath"), false));
            String format = "%s Q0 %s %d %.18f reference\n";

            for (String qid: testOutput.keySet()) {
                StringBuilder sb = new StringBuilder();
                ScoreList result = testOutput.get(qid);
                if (result.size() < 1) {
                    sb.append(String.format(format, qid, "dummy", 1, 0.0));
                } else {
                    for (int i = 0; i < Math.min(number, result.size()); i++) {
                        sb.append(String.format(format, qid,
                                Idx.getExternalDocid(result.getDocid(i)),
                                i+1, result.getDocidScore(i)));
                    }
                }
                output.write(sb.toString());
            }
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void test(Map<String, Map<Integer, FloatMatrix>> testRepresent,
                      Map<String, Map<Integer, FloatMatrix>> testIdfWts,
                      Map<String, ScoreList> testOutput) {
        for (String qid: testRepresent.keySet()) {
            ScoreList scoreList = new ScoreList();
            Map<Integer, FloatMatrix> curRepresent = testRepresent.get(qid);
            Map<Integer, FloatMatrix> curIdfWts = testIdfWts.get(qid);
            for (int docid:curRepresent.keySet()) {
                FloatMatrix inputRepresent = curRepresent.get(docid);
                FloatMatrix inputIdfWts = curIdfWts.get(docid);
                FloatMatrix output = this.mlp.forward(inputRepresent);
                FloatMatrix scores = this.wtdSum.forward(output, output, inputIdfWts);
                scoreList.add(docid, scores.get(0));
            }
            scoreList.sort();
            testOutput.put(qid, scoreList);
        }
    }

    private void prepareTest(Map<String,String> params, List<String> testQids,
                         Map<String, String> testQueries,
                         Map<String, ScoreList> testScoreLists,
                         Map<String, Map<Integer, FloatMatrix>> testRepresent,
                             Map<String, Map<Integer, FloatMatrix>> testIdfWts) {


        try {
            Map<String, FloatMatrix> queryMatrix = new HashMap<>();
            Map<String, FloatMatrix> idfWtsMatrix = new HashMap<>();
            for (String qid:testQids) {
                String[] queryToks = QryParser.tokenizeString(testQueries.get(qid));
                List<FloatMatrix> allFloatMatrix = new ArrayList<>();
                List<Float> allFloat = new ArrayList<>();
                for (String term:queryToks) {

                    float docNum = (float)Idx.getDocCount("title");
                    FloatMatrix termMatrix = IdxWordvec.get(term);
                    if (termMatrix != null) {
                        allFloatMatrix.add(termMatrix);
                        float df = (float) Idx.getDocFreq("title", term);
                        // float tmpFloat=Math.max(0.0F,
                        //         (float)Math.log((docNum- df + 0.5F) / ( df + 0.5F)));
                        float tmpFloat = (float) Math.log((docNum-df+0.5)/(df+0.5));
                        allFloat.add(tmpFloat);
                    }

                }
                if (allFloatMatrix.size()!=0) {
                    int N = allFloatMatrix.size();
                    FloatMatrix tmpMatrix1 = new FloatMatrix(N, 300);
                    FloatMatrix tmpMatrix2 = new FloatMatrix(N, 1);
                    for (int i=0;i<N;i++) {
                        tmpMatrix1.putRow(i, allFloatMatrix.get(i));
                        tmpMatrix2.put(i, allFloat.get(i));
                    }
                    queryMatrix.put(qid, tmpMatrix1);
                    tmpMatrix2 = MatrixFunctions.exp(tmpMatrix2).divi(
                            MatrixFunctions.exp(tmpMatrix2).sum());
                    idfWtsMatrix.put(qid, tmpMatrix2);
                }
            }


            for (String qid:testScoreLists.keySet()) {
                ScoreList curScoreList = testScoreLists.get(qid);
                for (int i=0;i<curScoreList.size();i++) {
                    List<String> toks = new ArrayList<>();
                    List<FloatMatrix> mat = new ArrayList<>();
                    int docId = curScoreList.getDocid(i);
                    TermVector vec = new TermVector(docId, "title");
                    for (int j=0;j<vec.positionsLength();j++) {
                        if (vec.stemString(vec.stemAt(j))!=null)
                            toks.add(vec.stemString(vec.stemAt(j)));
                    }
                    String[] newToks = QryParser.tokenizeString(String.join(" ", toks));
                    FloatMatrix qMatrix = queryMatrix.get(qid);
                    for (String w:newToks) {
                        FloatMatrix tmp = IdxWordvec.get(w);
                        if (tmp!=null) mat.add(tmp);
                    }

                    if (qMatrix!=null && mat.size()!=0) {
                        FloatMatrix bin = getBin(params, mat, qMatrix);
                        testRepresent.computeIfAbsent(qid, k->new HashMap<>()).put(docId,
                                bin);
                        testIdfWts.computeIfAbsent(qid, k->new HashMap<>()).put(docId,
                                idfWtsMatrix.get(qid));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void train(Map<String, String> params, List<FloatMatrix> trainRel,
                       List<FloatMatrix> trainNonRel, List<FloatMatrix> trainIdfWts) {

        int num_epochs = Integer.parseInt(params.get("drmm:numEpochs"));
        int batch_size = 1;
        double[] trainingLosses = new double[num_epochs];

        String[] layerUnits = params.get("drmm:mlpLayers").split(",");
        int nBins = Integer.parseInt(params.get("drmm:numHistogramBins"));
        int mlpLayers = layerUnits.length;
        
        int[] hiddens = new int[mlpLayers];
        for (int i=0;i<mlpLayers;i++) {
            hiddens[i] = Integer.parseInt(layerUnits[i]);
        }

        ArrayList<Activation> activations = new ArrayList<>();
        for (int i=0;i<mlpLayers;i++) {
            activations.add(new Tanh());
        }
        activations.add(new Identity());

        float lr = 0.01F;
        int linearSeed = Integer.parseInt(params.get("drmm:randomSeed"));
        int shuffleSeed = Integer.parseInt(params.get("drmm:randomSeed"));

        //MLP mlp = new MLP(784, 10, new int[]{512, 256, 128}, activations, new SoftmaxCrossEntropy(), 0.01F);
        MLP mlp = new MLP(nBins, 1, hiddens, activations, lr, linearSeed);
        this.mlp = mlp;
        WeightedSum wtdSum = new WeightedSum();
        this.wtdSum = wtdSum;
        Criterion criterion = new HingeLoss(1F);

        List<Integer> indices = IntStream.range(0, trainRel.size()).boxed().collect(Collectors.toList());

        for (int i = 0; i < num_epochs; i++) {
            long startTime = System.nanoTime();
            Collections.shuffle(indices, new Random(shuffleSeed));
            int[] train_indices = indices.stream().mapToInt(j -> j).toArray();
            ArrayList<Float> loss = new ArrayList<>();

            for (int train_indice:train_indices) {
                mlp.zero_grad();
                FloatMatrix inputRel = trainRel.get(train_indice);
                FloatMatrix inputNonRel = trainNonRel.get(train_indice);
                FloatMatrix inputIdfWts = trainIdfWts.get(train_indice);
                FloatMatrix input = FloatMatrix.concatVertically(inputRel, inputNonRel);
                FloatMatrix output = mlp.forward(input);

                int rows = output.rows;
                int[] up = new int[rows/2];
                int[] down = new int[rows/2];
                for (int j=0;j<rows/2;j++) {
                    up[j] = j;
                    down[j] = j+rows/2;
                }

                FloatMatrix outputRel = output.getRows(up);
                FloatMatrix outputNonRel = output.getRows(down);
                FloatMatrix scores = wtdSum.forward(outputRel, outputNonRel, inputIdfWts);
                FloatMatrix lossOutputs = criterion.forward(scores.getRow(0), scores.getRow(1));
                FloatMatrix delta = criterion.derivative();
                this.backward(delta);
                mlp.adagradStep();
                loss.add(lossOutputs.sum() / batch_size);
            }


            trainingLosses[i] = loss.stream().mapToDouble(val -> val).average().orElse(0.0);

//            System.out.println("Epoch " + i);
//            System.out.println("Training Loss: " + trainingLosses[i]);
//
//            System.out.println("Time taken for an epoch: " + (System.nanoTime() - startTime) / 1000000000 + "s");
//            System.out.println("******************************************");

        }

    }

    public void backward(FloatMatrix deltaScores) {
        FloatMatrix mlpOuts = this.wtdSum.backward(deltaScores);
        this.mlp.backward(mlpOuts);
    }

    private void sampleFromFile(Map<String, String> params,
                                List<String> trainQids,
                                Map<String, String>  trainQueries,
                                Map<String, ScoreList> trainScoreLists,
                                Map<String, Map<Integer, Integer>> trainRelLists,
                                List<FloatMatrix> trainRel,
                                List<FloatMatrix> trainNonRel,
                                List<FloatMatrix> trainIdfWts
                                ) {
        Map<String, FloatMatrix> queryMatrix = new HashMap<>();
        Map<String, FloatMatrix> idfWtsMatrix = new HashMap<>();
        try {
            float docNum = (float)Idx.getDocCount("title");
            for (String qid: trainQids) {
                String[] queryToks = QryParser.tokenizeString(trainQueries.get(qid));
                List<FloatMatrix> allFloatMatrix = new ArrayList<>();
                List<Float> allFloat = new ArrayList<>();
                for (String term:queryToks) {
                        FloatMatrix termMatrix = IdxWordvec.get(term);
                        if (termMatrix != null) {
                            allFloatMatrix.add(termMatrix);
                            float df = (float) Idx.getDocFreq("title", term);
                            // float tmpFloat=Math.max(0.0F,
                            // (float) Math.log((docNum-df+0.5)/(df+0.5)));
                            float tmpFloat = (float) Math.log((docNum-df+0.5)/(df+0.5));
                            allFloat.add(tmpFloat);
                        }
                }

                if (allFloatMatrix.size()!=0) {
                    int N = allFloatMatrix.size();
                    FloatMatrix tmpMatrix1 = new FloatMatrix(N, 300);
                    FloatMatrix tmpMatrix2 = new FloatMatrix(N, 1);
                    for (int i=0;i<N;i++) {
                        tmpMatrix1.putRow(i, allFloatMatrix.get(i));
                        tmpMatrix2.put(i, allFloat.get(i));
                    }
                    queryMatrix.put(qid, tmpMatrix1);


                    tmpMatrix2 = MatrixFunctions.exp(tmpMatrix2).divi(
                            MatrixFunctions.exp(tmpMatrix2).sum());

                    idfWtsMatrix.put(qid, tmpMatrix2);
                }

            }
        } catch (Exception e) {e.printStackTrace();}

        int numTrainingPairs = Integer.parseInt(params.get("drmm:numTrainingPairs"));
        Random random = new Random(Integer.parseInt(params.get("drmm:randomSeed")));

        String sampleFileName = params.get("drmm:sampleFile");

        String binFileName = params.get("drmm:binWeightFile");
        BufferedWriter output2 = null;

        try {
            output2 = new BufferedWriter(new FileWriter(binFileName, false));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(sampleFileName));
            String line;
            while ((line = reader.readLine())!=null) {
                String[] info = line.split("[,\\' \\']");
                String qid = info[0];
                ScoreList curScoreList = trainScoreLists.get(qid);
                Map<Integer, Integer> curRelList = trainRelLists.get(qid);
                String externalId1 = info[1];
                String externalId2 = info[2];
                System.err.printf("doc name: %s, %s.", externalId1, externalId2);
                
                
                int docId1 = Idx.getInternalDocid(externalId1);
                int docId2 = Idx.getInternalDocid(externalId2);

                int rel1 = curRelList.getOrDefault(docId1, 0);
                int rel2 = curRelList.getOrDefault(docId2, 0);

                TermVector vec1 = new TermVector(docId1, "title");
                TermVector vec2 = new TermVector(docId2, "title");
                List<FloatMatrix> mat1 = new ArrayList<>();
                List<FloatMatrix> mat2 = new ArrayList<>();
                List<String> toks1 = new ArrayList<>();
                List<String> toks2 = new ArrayList<>();

                for (int i=0;i<vec1.positionsLength();i++) {
                    if (vec1.stemString(vec1.stemAt(i))!=null)
                        toks1.add(vec1.stemString(vec1.stemAt(i)));
                }

                String[] newToks1 = QryParser.tokenizeString(String.join(" ", toks1));
                for (String w:newToks1) {
                    FloatMatrix tmp = IdxWordvec.get(w);
                    if (tmp!=null) mat1.add(tmp);
                }

                for (int i=0;i<vec2.positionsLength();i++) {
                    if (vec2.stemString(vec2.stemAt(i))!=null)
                        toks2.add(vec2.stemString(vec2.stemAt(i)));
                }
                String[] newToks2 = QryParser.tokenizeString(String.join(" ", toks2));
                for (String w:newToks2) {
                    FloatMatrix tmp = IdxWordvec.get(w);
                    if (tmp!=null) mat2.add(tmp);
                }
        
                if (mat1.size()==0 || mat2.size()==0) {continue;}
                FloatMatrix qMatrix = queryMatrix.get(qid);
                FloatMatrix bin1 = getBin(params, mat1, qMatrix);
                FloatMatrix bin2 = getBin(params, mat2, qMatrix);
                if (rel1 > rel2) {
                    trainRel.add(bin1);
                    trainNonRel.add(bin2);
                } else {
                    trainRel.add(bin2);
                    trainNonRel.add(bin1);
                }
                trainIdfWts.add(idfWtsMatrix.get(qid));

               // PrintOut bins
               if (output2!=null) {
                int idx =trainRel.size()-1;
                output2.write(idfWtsMatrix.get(qid).toString("%.10f")+ 
                     " # "+idx+","+"qid,"+qid+"\n");
 
                if (rel1>rel2) {
                    output2.write(bin1.toString("%.10f")+" # "+idx+",rel,"+Idx.getExternalDocid(docId1)+"\n");
                    output2.write(bin2.toString("%.10f")+" # "+idx+",nonrel,"+Idx.getExternalDocid(docId2)+"\n");
                } else {
                    output2.write(bin2.toString("%.10f")+" # "+idx+",rel,"+Idx.getExternalDocid(docId2)+"\n");
                    output2.write(bin1.toString("%.10f")+" # "+idx+",nonrel,"+Idx.getExternalDocid(docId1)+"\n");
                }
               }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        try {
            output2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }    

    private void sample(Map<String, String> params,
                        List<String> trainQids,
                        Map<String, String>  trainQueries,
                        Map<String, ScoreList> trainScoreLists,
                        Map<String, Map<Integer, Integer>> trainRelLists,
                        List<FloatMatrix> trainRel,
                        List<FloatMatrix> trainNonRel,
                        List<FloatMatrix> trainIdfWts) {

        String sampleFileName = params.get("drmm:sampleFile");
        if (sampleFileName!=null && (new File(sampleFileName)).exists()) {
            sampleFromFile(params, trainQids, trainQueries, trainScoreLists, 
                        trainRelLists, trainRel, trainNonRel, trainIdfWts);
            return ;
        }
        Map<String, FloatMatrix> queryMatrix = new HashMap<>();
        Map<String, FloatMatrix> idfWtsMatrix = new HashMap<>();
        try {
            float docNum = (float)Idx.getDocCount("title");
            for (String qid: trainQids) {
                String[] queryToks = QryParser.tokenizeString(trainQueries.get(qid));
                List<FloatMatrix> allFloatMatrix = new ArrayList<>();
                List<Float> allFloat = new ArrayList<>();
                for (String term:queryToks) {
                        FloatMatrix termMatrix = IdxWordvec.get(term);
                        if (termMatrix != null) {
                            allFloatMatrix.add(termMatrix);
                            float df = (float) Idx.getDocFreq("title", term);
                            // float tmpFloat=Math.max(0.0F,
                            //         (float)Math.log((docNum- df + 0.5F) / ( df + 0.5F)));
                        float tmpFloat = (float) Math.log((docNum-df+0.5)/(df+0.5));
                            allFloat.add(tmpFloat);
                        }

                }

                if (allFloatMatrix.size()!=0) {
                    int N = allFloatMatrix.size();
                    FloatMatrix tmpMatrix1 = new FloatMatrix(N, 300);
                    FloatMatrix tmpMatrix2 = new FloatMatrix(N, 1);
                    for (int i=0;i<N;i++) {
                        tmpMatrix1.putRow(i, allFloatMatrix.get(i));
                        tmpMatrix2.put(i, allFloat.get(i));
                    }
                    queryMatrix.put(qid, tmpMatrix1);

                    tmpMatrix2 = MatrixFunctions.exp(tmpMatrix2).divi(
                            MatrixFunctions.exp(tmpMatrix2).sum());
                    // tmpMatrix2.softmax(tmpMatrix2);
                    idfWtsMatrix.put(qid, tmpMatrix2);
                }

            }
        } catch (Exception e) {e.printStackTrace();}

        int numTrainingPairs = Integer.parseInt(params.get("drmm:numTrainingPairs"));
        Random random = new Random(Integer.parseInt(params.get("drmm:randomSeed")));

        String binFileName = params.get("drmm:binWeightFile");
        BufferedWriter output2 = null;
        // BufferedWriter output1 = null;

        try {
            // output1 = new BufferedWriter(new FileWriter(sampleFileName, false));
            output2 = new BufferedWriter(new FileWriter(binFileName, false));
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (trainRel.size() < numTrainingPairs) {
            int queryId = random.nextInt(trainQids.size());
            String qid = trainQids.get(queryId);
            ScoreList curScoreList = trainScoreLists.get(qid);
            Map<Integer, Integer> curRelList = trainRelLists.get(qid);

            int docId1=0, docId2=0, rel1=0, rel2=0;

            while (docId1==docId2 || rel1==rel2) {
                docId1 = curScoreList.getDocid(random.nextInt(curScoreList.size()));
                docId2 = curScoreList.getDocid(random.nextInt(curScoreList.size()));
                rel1 = curRelList.getOrDefault(docId1, 0);
                rel2 = curRelList.getOrDefault(docId2, 0);
            }

            if (queryMatrix.get(qid)==null) {continue;}

            try {
                TermVector vec1 = new TermVector(docId1, "title");
                TermVector vec2 = new TermVector(docId2, "title");
                List<FloatMatrix> mat1 = new ArrayList<>();
                List<FloatMatrix> mat2 = new ArrayList<>();
                List<String> toks1 = new ArrayList<>();
                List<String> toks2 = new ArrayList<>();

                for (int i=0;i<vec1.positionsLength();i++) {
                    if (vec1.stemString(vec1.stemAt(i))!=null)
                        toks1.add(vec1.stemString(vec1.stemAt(i)));
                }

                String[] newToks1 = QryParser.tokenizeString(String.join(" ", toks1));
                for (String w:newToks1) {
                    FloatMatrix tmp = IdxWordvec.get(w);
                    if (tmp!=null) mat1.add(tmp);
                }

                for (int i=0;i<vec2.positionsLength();i++) {
                    if (vec2.stemString(vec2.stemAt(i))!=null)
                        toks2.add(vec2.stemString(vec2.stemAt(i)));
                }
                String[] newToks2 = QryParser.tokenizeString(String.join(" ", toks2));
                for (String w:newToks2) {
                    FloatMatrix tmp = IdxWordvec.get(w);
                    if (tmp!=null) mat2.add(tmp);
                }

                if (mat1.size()==0 || mat2.size()==0) {continue;}
                FloatMatrix qMatrix = queryMatrix.get(qid);
                FloatMatrix bin1 = getBin(params, mat1, qMatrix);
                FloatMatrix bin2 = getBin(params, mat2, qMatrix);
                if (rel1 > rel2) {
                    trainRel.add(bin1);
                    trainNonRel.add(bin2);
                } else {
                    trainRel.add(bin2);
                    trainNonRel.add(bin1);
                }
                trainIdfWts.add(idfWtsMatrix.get(qid));


               // PrintOut bins
               if (output2!=null) {
                int idx =trainRel.size()-1;
                output2.write(idfWtsMatrix.get(qid).toString("%.10f")+ 
                     " # "+idx+","+"qid,"+qid+"\n");
 
                if (rel1>rel2) {
                    output2.write(bin1.toString("%.10f")+" # "+idx+",rel,"+Idx.getExternalDocid(docId1)+"\n");
                    output2.write(bin2.toString("%.10f")+" # "+idx+",nonrel,"+Idx.getExternalDocid(docId2)+"\n");
                } else {
                    output2.write(bin2.toString("%.10f")+" # "+idx+",rel,"+Idx.getExternalDocid(docId2)+"\n");
                    output2.write(bin1.toString("%.10f")+" # "+idx+",nonrel,"+Idx.getExternalDocid(docId1)+"\n");
                }
               }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            // output1.close();
            output2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private FloatMatrix getBin(Map<String, String> params, List<FloatMatrix> mat,
                               FloatMatrix qidMatrix) {
        int nBins = Integer.parseInt(params.get("drmm:numHistogramBins"));
        FloatMatrix res = new FloatMatrix(qidMatrix.rows, nBins);
        for (int i=0;i<qidMatrix.rows;i++) {
            FloatMatrix row = qidMatrix.getRow(i);
            FloatMatrix bin = FloatMatrix.zeros(1, nBins);
            for (FloatMatrix m: mat) {
//                System.out.printf("%d,%d\n", row.rows, row.columns);
//                System.out.printf("%d,%d\n", m.rows, m.columns);
                float s =  SimpleBlas.dot(row,m) /
                        ( SimpleBlas.nrm2(row) *
                         SimpleBlas.nrm2(m));
                if (s>=0.99999F) {
                    bin.put(nBins-1, bin.get(nBins-1)+1);
                } else {
                    int idx = (int) Math.floor((s+1F)/1.99999F*(nBins-1));
                    bin.put(idx, bin.get(idx)+1);
                }
            }
            for (int j=0;j<nBins;j++) {
                if (bin.get(j)>0F)
                    bin.put(j, (float)Math.log(bin.get(j)));
            }
            res.putRow(i, bin);
        }
        return res;
    }

    // Get input data
    private void prepareTrainRelLists(Map<String, String> params,
                                   Map<String, Map<Integer, Integer>> relLists) {
        try {
            BufferedReader input = new BufferedReader(new FileReader(params.get("letor:trainingQrelsFile")));
            String qLine;
            String prevQid = null;
            Map<Integer, Integer> tmp = new HashMap<>();

            while ((qLine = input.readLine())!=null) {
                String[] tokens = qLine.split(" ");
                if (!tokens[0].equals(prevQid) && tmp.size() != 0) {
                    relLists.put(prevQid, tmp);
                    tmp = new HashMap<>();
                }
                tmp.put(Idx.getInternalDocid(tokens[2]), Integer.parseInt(tokens[3]));
                prevQid = tokens[0];
            }
            if (tmp.size()!=0) {
                relLists.put(prevQid, tmp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareTrainScoreLists(Map<String, String> params,
                                        Map<String, ScoreList> trainScoreLists,
                                        Map<String, String> trainQueryies,
                                        List<String> trainQids) {
        String trainingRankingFile = params.get("drmm:trainingRankingFile");
        if (trainingRankingFile == null) {
            processQueryFile(params, params.get("letor:trainingQueryFile"), trainScoreLists);
        } else {
            readRankingFile(trainingRankingFile, trainScoreLists);
        }
        readQueryFile(params.get("letor:trainingQueryFile"), trainQids, trainQueryies);
    }

    private void prepareTestScoreList(Map<String, String> params,
                                      Map<String, ScoreList> testScoreLists,
                                      Map<String, String> testQueryies,
                                      List<String> testQids) {
        String testingRankingFile = params.get("drmm:testingRankingFile");
        if (testingRankingFile == null) {
            processQueryFile(params, params.get("queryFilePath"), testScoreLists);
        } else {
            readRankingFile(testingRankingFile, testScoreLists);
        }
        if (params.get("rerank:maxInputRankingsLength")!=null) {
            int size = Integer.parseInt(params.get("rerank:maxInputRankingsLength"));
            for (String qid:testScoreLists.keySet()) {
                ScoreList tmp = new ScoreList();
                for (int i=0;i<Math.min(testScoreLists.get(qid).size(), size);i++) {
                    tmp.add(testScoreLists.get(qid).getDocid(i), testScoreLists.get(qid).getDocidScore(i));
                }
                testScoreLists.put(qid, tmp);

            }
        }


        readQueryFile(params.get("queryFilePath"), testQids, testQueryies);
    }

    private void readQueryFile(String queryFile, List<String> qids, Map<String, String> queries) {
        try {
            String qLine;
            BufferedReader input = new BufferedReader(new FileReader(queryFile));
            while ((qLine=input.readLine())!=null) {
                String[] tokens = qLine.split(":", 2);
                qids.add(tokens[0].trim());
                queries.put(tokens[0].trim(), tokens[1].trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readRankingFile(String RankingFile, Map<String, ScoreList> scoreLists) {
        try {
            BufferedReader input = new BufferedReader(new FileReader(RankingFile));
            String qLine;
            String prevQid = null;
            ScoreList tmp = new ScoreList();

            while ((qLine = input.readLine())!=null) {
                String[] tokens = qLine.split(" ");
                if (!tokens[0].equals(prevQid) && tmp.size() != 0) {
                    scoreLists.put(prevQid, tmp);
                    tmp = new ScoreList();
                }
                tmp.add(Idx.getInternalDocid(tokens[2]), Double.parseDouble(tokens[4]));
                prevQid = tokens[0];
            }
            if (tmp.size()!=0) {
                tmp.sort();
                scoreLists.put(prevQid, tmp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processQueryFile(Map<String, String> params, String queryFile,
                                  Map<String, ScoreList> scoreLists) {
        int maxInputRankingsLength = Integer.parseInt(params.get("rerank:maxInputRankingsLength"));
        QryExpand expander = new QryExpand(params);
        QryDiversity diversity = new QryDiversity(params);

        BufferedReader input = null;
        try {
            RetrievalModel model = QryEval.initializeRetrievalModel(params);
            String qLine;
            input = new BufferedReader(new FileReader(queryFile));

            //  Each pass of the loop processes one query.
            while ((qLine = input.readLine()) != null) {
                qLine = expander.expand(qLine, model.defaultQrySopName());
                System.out.println("Query " + qLine);
                String[] pair = qLine.split(":");

                String qid = pair[0];
                String query = pair[1];
                ScoreList results = diversity.processQuery(qid, query, model);

                if (results != null) {
                    results.sort();
                    ScoreList tmp = new ScoreList();
                    for (int i=0;i<Math.min(results.size(), maxInputRankingsLength);i++){
                        tmp.add(results.getDocid(i), results.getDocidScore(i));
                    }
                    scoreLists.put(qid, tmp);
                }
            }
            input.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
