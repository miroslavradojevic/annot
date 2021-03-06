package Sift;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import mpi.cbg.fly.Feature;
import mpi.cbg.fly.Filter;
import mpi.cbg.fly.FloatArray2D;
import mpi.cbg.fly.FloatArray2DSIFT;
import mpi.cbg.fly.FloatArray2DScaleOctave;
import mpi.cbg.fly.ImageArrayConverter;
import weka.classifiers.Classifier;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.filters.unsupervised.attribute.Remove;

/**
 *
 * @author Gadea
 */
public class Sift_scoreMap implements PlugIn {

    ImagePlus inimg;
    String imgPath, path;
    int D, W, H, N;

    //parameters to calculate SIFT features
    // steps
    private static int steps = 3;
    // initial sigma
    private static float initial_sigma = 1.6f;
    // feature descriptor size
    private static int fdsize = 4;
    // feature descriptor orientation bins
    private static int fdbins = 8;
    // size restrictions for scale octaves, use octaves < max_size and > min_size only
    private static int min_size = 64;
    private static int max_size = 1024;
    private static boolean upscale = false;
    private static float scale = 1.0f;

    int ngroups; //number of groups for BoW
    String path_modelBoW; //path where is the model-file for BoW.
    String path_modelNB; //path where is the model-file for classifying

    ArrayList<InstFeatures> listData = new ArrayList<InstFeatures>();
    ArrayList<OneFeature> allF = new ArrayList<OneFeature>();

    ArrayList<InstFeatures> listDataClass = new ArrayList<InstFeatures>();
    ArrayList<OneFeature> allF_Class = new ArrayList<OneFeature>();

    String expName = "test_"; //name of experiment or mosaic

    @Override
    public void run(String arg) {

        imgPath = Prefs.get("score.imagePath", System.getProperty("user.home"));
        path = Prefs.get("score.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("score.D", 500); 
        path_modelBoW = Prefs.get("score.modelBoW", System.getProperty("user.home")); //path where is the model-file for BoW.
        path_modelNB = Prefs.get("score.modelNB", System.getProperty("user.home"));
        N = (int) Prefs.get("score.N", 300);
        ngroups = (int) Prefs.get("score.ngroups", 20);

        GenericDialog gdG = new GenericDialog("MosaicDetection");
        gdG.addStringField("path of image", imgPath, 80);
        gdG.addNumericField("square_size", D, 0);
        gdG.addNumericField("nr_test_patches", N, 0);
        gdG.addStringField("destination_folder", path, 80);
        gdG.addMessage("--- to calculate SIFT features ---");
        gdG.addNumericField("steps_per_scale_octave :", steps, 0);
        gdG.addNumericField("initial_gaussian_blur :", initial_sigma, 2);
        gdG.addNumericField("feature_descriptor_size :", fdsize, 0);
        gdG.addNumericField("feature_descriptor_orientation_bins :", fdbins, 0);
        gdG.addNumericField("minimum_image_size :", min_size, 0);
        gdG.addNumericField("maximum_image_size :", max_size, 0);
        gdG.addCheckbox("upscale_image_first", upscale);
        gdG.addMessage("--- to calculate Bag of Words ---");
        gdG.addNumericField("number_words :", ngroups, 0);
        gdG.addMessage("--- models for BoW and Naïve Bayes ---");
        gdG.addStringField("modelBoW", path_modelBoW, 80);
        gdG.addStringField("modelNB", path_modelNB, 80);

        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }

        imgPath = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("score.imagePath", imgPath);
        D = (int) gdG.getNextNumber();
        Prefs.set("score.D", D);
        N = (int) gdG.getNextNumber();
        Prefs.set("score.N", N);
        path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("score.destination_folder", path);
        steps = (int) gdG.getNextNumber();
        initial_sigma = (float) gdG.getNextNumber();
        fdsize = (int) gdG.getNextNumber();
        fdbins = (int) gdG.getNextNumber();
        min_size = (int) gdG.getNextNumber();
        max_size = (int) gdG.getNextNumber();
        upscale = gdG.getNextBoolean();
        ngroups = (int) gdG.getNextNumber();
        Prefs.set("score.ngroups", ngroups);
        if (upscale) {
            scale = 2.0f;
        }
        path_modelBoW = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("score.modelBoW", path_modelBoW);
        path_modelNB = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("score.modelNB", path_modelNB);

        inimg = new ImagePlus(imgPath);

        if (inimg == null) {
            return;
        }

        long start_time = System.currentTimeMillis();

        H = inimg.getHeight();
        W = inimg.getWidth();

        //to do a grid
        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();
        }
        String pathh = f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_testPatches"; //
        f = new File(pathh);
        if (!f.exists()) {
            f.mkdirs();
        }
        Overlay ov = new Overlay();
        int margin = D / 2;
        int step = (int) Math.floor(Math.sqrt(((W - D) * (H - D)) / N));
        //calculate the patches
        ArrayList<Point> pointList = new ArrayList<Point>();
        ArrayList<ImagePlus> patchesList = new ArrayList<ImagePlus>();

        for (int x = margin; x < W - margin - D; x += step) {
            for (int y = margin; y < H - margin - D; y += step) {

                Rectangle rec = new Rectangle(x, y, D, D);
                Roi rec_roi = new Roi(rec);
                ov.add(rec_roi);

                inimg.setOverlay(ov);
                inimg.updateAndDraw();

                pointList.add(new Point(x, y));

//              extract the patch
                ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
                ipCopy.setRoi(rec_roi);
                ipCopy = ipCopy.crop();
                ImagePlus impCopy = new ImagePlus("", ipCopy);
                patchesList.add(impCopy);
            }

        }
        inimg.show();
        //generate the SIFTFeatures from patches to classify
        ArrayList<InstFeatures> listDataClass = new ArrayList<InstFeatures>();
        listDataClass = getSIFTFeatures(patchesList);

        IJ.log("Weka clustering (SKM):");
        //generate the instances for Weka and fill allF_Class
        Instances ins_data_class = getInstancesClustering(listDataClass, ngroups);
        IJ.log("Generating arff-file...");
        extractArffClustering(ins_data_class, expName + "_class");
        try {
            allF_Class = loadSKMClassifier(path_modelBoW, ins_data_class, allF_Class);
        } catch (Exception ex) {
            Logger.getLogger(Sift_class.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.log("1-");
        ArrayList<InstHistograms> instHistoClass = getHistograms(allF_Class, ngroups);
        IJ.log("2-");
        Instances ins_data_histo_class = getInstancesHistogram(instHistoClass, false);
        IJ.log("3-");
        extractArffClustering(ins_data_histo_class, expName + "_histo_class");
        try {
            ArrayList<Integer> classPred = loadNBClassifier(path_modelNB, ins_data_histo_class, pointList);
            drawRectangles(pointList, classPred);
            IJ.log("This process took " + (System.currentTimeMillis() - start_time) + "ms");
            IJ.log("The process has finished.");
        } catch (Exception ex) {
            IJ.log("exception NB");
            Logger.getLogger(Sift_class.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    protected ArrayList<InstHistograms> getHistograms(ArrayList<OneFeature> allF, int ngroups) {
        ArrayList<InstHistograms> instHist = new ArrayList<InstHistograms>();
        ArrayList<String> nameList = new ArrayList<String>();
        ArrayList<Integer[]> histogram = new ArrayList<Integer[]>();
        ArrayList<Integer> clList = new ArrayList<Integer>();
        for (OneFeature of : allF) {
            //if the patch was not study before, it creates a new instance and its instogram is initialized.
            if (!nameList.contains(of.getName())) {
                nameList.add(of.getName());
                clList.add(of.getCl());
                histogram.add(new Integer[ngroups]);
                for (int i = 0; i < ngroups; i++) {
                    histogram.get(histogram.size() - 1)[i] = 0;
                }
            }
            int index = nameList.indexOf(of.getName());
            histogram.get(index)[of.getCluster()]++;
        }

        for (int i = 0; i < nameList.size(); i++) {
            instHist.add(new InstHistograms(histogram.get(i), nameList.get(i), clList.get(i)));
        }
        IJ.log("number of histograms: " + String.valueOf(instHist.size()));//it has to be the same number that the patches
        return instHist;
    }

    protected Instances getInstancesClustering(ArrayList<InstFeatures> listData, int ngroups) {
        //names to the instances
        String[] names = new String[128];
        for (int k = 0; k < 128; k++) {
            names[k] = "desccriptor_" + k;
        }
        int ncolumns = names.length + 1;//number of features
        int nrows = listData.size();//number of instances

        // We create a feature vector and the attributes to the feature vector
        FastVector fvWekaAttributes = new FastVector(ncolumns);

        for (int i = 0; i < names.length; i++) {
            fvWekaAttributes.addElement(new Attribute(names[i]));
        }
        // Declare the class attribute along with its values
        FastVector fvClassVal = new FastVector(3);
        fvClassVal.addElement("1");//neuron
        fvClassVal.addElement("2");//background
        fvClassVal.addElement("3");//other
        Attribute ClassAttribute = new Attribute("class", fvClassVal);

        fvWekaAttributes.addElement(ClassAttribute);
        // We create an empty set of instances
        Instances ins_data = new Instances("Rel", fvWekaAttributes, ncolumns);
        ins_data.setClassIndex(names.length); //128
        // We create the instances and add them to the set of instances
        for (int i = 0; i < nrows; i++) {
            Instance ins = new DenseInstance(ncolumns);
            if (listData.get(i).getFs1().isEmpty()) { //initialize
                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(n), 0);
                }
                ins.setMissing(128);
                ins_data.add(ins);
                if (listData.get(i).getName().equals("")) {
                    listData.get(i).setName(String.valueOf(i));
                }
                allF_Class.add(new OneFeature(null, listData.get(i).getCl(), listData.get(i).getName()));
            }
            for (Feature f : listData.get(i).getFs1()) {
                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(n), f.descriptor[n]);
                }
                ins.setValue((Attribute) fvWekaAttributes.elementAt(128), String.valueOf(listData.get(i).getCl()));
                ins_data.add(ins);
                if (listData.get(i).getName().equals("")) {
                    listData.get(i).setName(String.valueOf(i));
                }
                allF_Class.add(new OneFeature(f, listData.get(i).getCl(), listData.get(i).getName()));
            }
        }
        return ins_data;
    }

    protected ArrayList<InstFeatures> getSIFTFeatures(ArrayList<ImagePlus> patchesList) {
        ArrayList<InstFeatures> listData = new ArrayList<InstFeatures>();
        ArrayList<ImagePlus> listImages = patchesList;
        IJ.log("one folder...");
        for (int i = 0; i < listImages.size(); i++) {

            Vector<Feature> fs1 = calculateSIFT(listImages.get(i));
            listData.add(new InstFeatures(fs1, 3, listImages.get(i).getTitle()));
        }
        IJ.log("Images with SIFT features saved");
        return listData;
    }

    protected Vector<Feature> calculateSIFT(ImagePlus imp) {
        ImageProcessor ip1 = imp.getProcessor().convertToFloat();
        Vector< Feature> fs1;

        FloatArray2DSIFT sift = new FloatArray2DSIFT(fdsize, fdbins);
        FloatArray2D fa = ImageArrayConverter.ImageToFloatArray2D(ip1);
        Filter.enhance(fa, 1.0f);
        if (upscale) {
            FloatArray2D fat = new FloatArray2D(fa.width * 2 - 1, fa.height * 2 - 1);
            FloatArray2DScaleOctave.upsample(fa, fat);
            fa = fat;
            fa = Filter.computeGaussianFastMirror(fa, (float) Math.sqrt(initial_sigma * initial_sigma - 1.0));
        } else {
            fa = Filter.computeGaussianFastMirror(fa, (float) Math.sqrt(initial_sigma * initial_sigma - 0.25));
        }
        sift.init(fa, steps, initial_sigma, min_size, max_size);
        fs1 = sift.run(max_size);
        Collections.sort(fs1);
        return fs1;
    }

    /**
     *
     * @param pathModel
     * @param ins_data
     * @param allF
     * @return
     * @throws Exception
     */
    public ArrayList<OneFeature> loadSKMClassifier(String pathModel, Instances ins_data, ArrayList<OneFeature> allF) throws Exception {

        Object[] obj = weka.core.SerializationHelper.readAll(pathModel);

        SimpleKMeans skm = (SimpleKMeans) obj[0];

        //the instance has 129 attributes: 128 descriptors plus one attribute more for the class
        //this filter removes the attribute class
        weka.filters.unsupervised.attribute.Remove filter = new Remove();
        filter.setAttributeIndices("129");
        filter.setInputFormat(ins_data);
        Instances data_without_class = weka.filters.Filter.useFilter(ins_data, filter);

        //perform your prediction
        for (int i = 0; i < data_without_class.size(); i++) {
            Instance aux_ins = data_without_class.get(i);
            int cluster = skm.clusterInstance(aux_ins);
            allF.get(i).setCluster(cluster);
        }
        return allF;
    }

    /**
     *
     * @param pathModel
     * @param ins_data
     * @throws Exception
     */
    public ArrayList<Integer> loadNBClassifier(String pathModel, Instances ins_data, ArrayList<Point> pointList) throws Exception {
        Classifier nb = (Classifier) weka.core.SerializationHelper.read(pathModel);
        ArrayList<Integer> classPred = new ArrayList<Integer>();
        for (int i = 0; i < ins_data.size(); i++) {
            // Get the likelihood of each classes
            double[] fDistribution = nb.distributionForInstance(ins_data.get(i));
            String aux = i + " ";
            double max = 0;
            int index = -1;
            for (int j = 0; j < fDistribution.length; j++) {
                if (max < fDistribution[j]) {
                    max = fDistribution[j];
                    index = j;
                }
            }

            classPred.add(index);
            IJ.log(pointList.get(i).getX() + "-" + pointList.get(i).getY() + ": " + classPred.get(i));
        }
        return classPred;

    }

    //to extract the instances to arff-file.
    public void extractArffClustering(Instances dataSet, String name) {
        ArffSaver saver = new ArffSaver();
        saver.setInstances(dataSet);
        try {
            saver.setFile(new File(path + File.separator + name + ".arff"));
            saver.writeBatch();
        } catch (Exception ex) {
            IJ.log(ex.getMessage());
        }
    }

    protected Instances getInstancesHistogram(ArrayList<InstHistograms> instHisto, boolean isTrain) {
        String[] names = new String[ngroups];
        for (int k = 0; k < ngroups; k++) {
            names[k] = "group_" + k;
        }
        IJ.log("classify...");
        int ncolumns = names.length + 1;//number or features for Weka
        int nrows = instHisto.size();//number of instances

        // We create a feature vector and the attributes to the feature vector
        FastVector fvWekaAttributes = new FastVector(ncolumns);
        for (int i = 0; i < names.length; i++) {
            fvWekaAttributes.addElement(new Attribute(names[i]));
        }
        FastVector fvClassVal = new FastVector(3);
        fvClassVal.addElement("1");
        fvClassVal.addElement("2");
        fvClassVal.addElement("3");
        Attribute ClassAttribute = new Attribute("class", fvClassVal);
        fvWekaAttributes.addElement(ClassAttribute);

        // We create an empty set of instances
        Instances ins_data_histo = new Instances("Rel", fvWekaAttributes, ncolumns);
        ins_data_histo.setClassIndex(names.length);
        // We create the instances and add them to the set of instances
        for (int i = 0; i < nrows; i++) {
            Instance ins = new DenseInstance(ncolumns);
            for (int n = 0; n < ngroups; n++) {
                ins.setValue((Attribute) fvWekaAttributes.elementAt(n), instHisto.get(i).getHistogram()[n]);
            }
            if (isTrain) {
                ins.setValue((Attribute) fvWekaAttributes.elementAt(ngroups), String.valueOf(instHisto.get(i).getCl()));
                ins_data_histo.add(ins);
            } else {
                ins.setMissing(ngroups);//.setValue((Attribute) fvWekaAttributes.elementAt(ngroups), String.valueOf(instHisto.get(i).getCl()));
                ins_data_histo.add(ins);
            }
        }
        return ins_data_histo;
    }

    public void drawRectangles(ArrayList<Point> pointList, ArrayList<Integer> classPred) {
        //to visualizate the results
        Overlay curr_ovl = new Overlay();
        for (int i = 0; i < pointList.size(); i++) {
            Rectangle rec = new Rectangle((int) pointList.get(i).getX(), (int) pointList.get(i).getY(), D, D);
            if (classPred.get(i) == 0) {
                //in red the patches are and have been classified like neurons
                Roi roi_to_add = new Roi(rec);
                roi_to_add.setFillColor(new Color(1, 0, 0, 0.2f));
                roi_to_add.setStrokeColor(new Color(1, 0, 0, 1));
                curr_ovl.add(roi_to_add);
            } else if (classPred.get(i) == 1) {
                //in blue the patches have been classified like background
                Roi roi_to_add = new Roi(rec);
                roi_to_add.setFillColor(new Color(0, 0, 1, 0.2f));
                roi_to_add.setStrokeColor(new Color(0, 0, 1, 1));
                curr_ovl.add(roi_to_add);
            } else if (classPred.get(i) == 2) {
                //yellow for the patches have been classified like astrocytes
                Roi roi_to_add = new Roi(rec);
                roi_to_add.setFillColor(new Color(1, 1, 0, 0.2f));
                roi_to_add.setStrokeColor(new Color(1, 1, 0, 1));
                curr_ovl.add(roi_to_add);
            }
        }
        ImagePlus imout = inimg.duplicate();
        imout.setOverlay(curr_ovl);
        imout.show();
        imout.updateAndDraw();
    }

}
