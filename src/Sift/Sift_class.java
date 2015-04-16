package Sift;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import java.awt.Point;
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
 *
 * to calculate the sift features, it uses the JavaSIFT library:
 * http://fly.mpi-cbg.de/~saalfeld/Projects/javasift.html
 *
 */
public class Sift_class implements PlugIn {

    String path_modelBoW; //path where is the model-file for BoW.
    String path_modelNB; //path where is the model-file for classifying
    String path_destination; //where are the images for studying
    String output_path;//path to save the arff-files
    String expName; //name of experiment or mosaic
    int ngroups = 20; //number of groups for BoW

    ArrayList<InstFeatures> listData = new ArrayList<InstFeatures>();
    ArrayList<OneFeature> allF = new ArrayList<OneFeature>();

    ArrayList<InstFeatures> listDataClass = new ArrayList<InstFeatures>();
    ArrayList<OneFeature> allF_Class = new ArrayList<OneFeature>();

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

    @Override
    public void run(String arg) {

        //-----------DIALOG-------
        path_modelBoW = Prefs.get("annot.modelBoW", System.getProperty("user.home"));
        path_modelNB = Prefs.get("annot.modelNB", System.getProperty("user.home"));
        expName = Prefs.get("annot.expName", "m01");
        path_destination = Prefs.get("annot.path_destination", System.getProperty("user.home"));
        output_path = Prefs.get("annot.output_path", System.getProperty("user.home"));

        GenericDialog gdG = new GenericDialog("SIFT Classify - Weka");
        gdG.addStringField("modelBoW", path_modelBoW, 80);
        gdG.addStringField("modelNB", path_modelNB, 80);
        gdG.addStringField("destination_path", path_destination, 80);
        gdG.addStringField("output_path", output_path, 80);
        gdG.addStringField("experiment_name", expName, 10);
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
        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }
        path_modelBoW = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.modelBoW", path_modelBoW);
        path_modelNB = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.modelNB", path_modelNB);
        path_destination = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.path_destination", path_destination);
        output_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.output_path", output_path);
        expName = gdG.getNextString();
        Prefs.set("annot.expName", expName);
        steps = (int) gdG.getNextNumber();
        initial_sigma = (float) gdG.getNextNumber();
        fdsize = (int) gdG.getNextNumber();
        fdbins = (int) gdG.getNextNumber();
        min_size = (int) gdG.getNextNumber();
        max_size = (int) gdG.getNextNumber();
        upscale = gdG.getNextBoolean();
        ngroups = (int) gdG.getNextNumber();
        Prefs.set("annot.ngroups", ngroups);
        if (upscale) {
            scale = 2.0f;
        }

        if (path_modelBoW.isEmpty() || path_modelNB.isEmpty() || path_destination.isEmpty()) {
            return;
        }
        long start_time = System.currentTimeMillis();
        //------- SECOND STEP: APPLY THE DETECTION ---------
        //generate the SIFTFeatures from patches to classify
        listDataClass = getSIFTFeatures(path_destination);

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
        ArrayList<InstHistograms> instHistoClass = getHistograms(allF_Class, ngroups);
        Instances ins_data_histo_class = getInstancesHistogram(instHistoClass, false);
        extractArffClustering(ins_data_histo_class, expName + "_histo_class");
        try {
            loadNBClassifier(path_modelNB, ins_data_histo_class);
        } catch (Exception ex) {
            Logger.getLogger(Sift_class.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.log("This process took " + (System.currentTimeMillis() - start_time) + "ms");
        IJ.log("The process has finished.");
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
    public void loadNBClassifier(String pathModel, Instances ins_data) throws Exception {
        Classifier nb = (Classifier) weka.core.SerializationHelper.read(pathModel);
        int[] neuron = {0, 0, 0};
        int[] background = {0, 0, 0};
        int[] astrocyte = {0, 0, 0};
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
            int type = getTypefromName(listDataClass.get(i).getName());
            aux = "predicted: " + index + " // real: " + type;
            if (type == 0) {
                if (index == 0) {
                    neuron[0]++;
                } else if (index == 1) {
                    neuron[1]++;
                } else if (index == 2) {
                    neuron[2]++;
                } else {
                    IJ.log("error with real type");
                }
            } else if (type == 1) {
                if (index == 0) {
                    background[0]++;
                } else if (index == 1) {
                    background[1]++;
                } else if (index == 2) {
                    background[2]++;
                } else {
                    IJ.log("error with real type");
                }
            } else if (type == 2) {
                if (index == 0) {
                    astrocyte[0]++;
                } else if (index == 1) {
                    astrocyte[1]++;
                } else if (index == 2) {
                    astrocyte[2]++;
                } else {
                    IJ.log("error with real type");
                }
            } else if (index == -1) {
                IJ.log("error with the classification");
            }
        }
        IJ.log("-------------confusion matrix-----------");
        IJ.log("           neuron, background, astrocyte");
        IJ.log("neuron " + String.valueOf(neuron[0]) + " - " + String.valueOf(neuron[1]) + " - " + String.valueOf(neuron[2]));
        IJ.log("background " + String.valueOf(background[0]) + " - " + String.valueOf(background[1]) + " - " + String.valueOf(background[2]));
        IJ.log("astrocyte " + String.valueOf(astrocyte[0]) + " - " + String.valueOf(astrocyte[1]) + " - " + String.valueOf(astrocyte[2]));
    }

    /**
     *
     * @param name has to be of this way: "name_m01_D500_b183"
     * @return
     */
    public int getTypefromName(String name) {
        String[] auxName = name.trim().split("_");
        if (auxName[3].contains("b")) {
            return 1;
        } else if (auxName[3].contains("a")) {
            return 2;
        } else if (auxName[3].contains("n")) {
            return 0;
        } else {
            IJ.log("error with the name, it has to be like this: name_m01_D500_b183");
            return -1;
        }
    }

    /**
     *
     * @param name has to be of this way: "m03,X,Y,D,i,61,4910,500,43"
     * @return
     */
    public Point getPointfromName(String name) {
        //it works ok if the patches has been got with the detection
        String[] auxName = name.trim().split(",");
        int x = Integer.valueOf(auxName[5]);
        int y = Integer.valueOf(auxName[6]);
        return new Point(x, y);
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

    /**
     * code from http://fly.mpi-cbg.de/~saalfeld/Projects/javasift.html
     *
     * @param path
     * @return
     */
    protected ArrayList<InstFeatures> getSIFTFeatures(String path) {
        ArrayList<InstFeatures> listData = new ArrayList<InstFeatures>();
        ArrayList<File> listDirClass = getClassFolder(path);
        if (listDirClass.isEmpty()) {
            IJ.log("no subfolders");
            ArrayList<ImagePlus> listImages = getImagesFolder(path);
            for (int i = 0; i < listImages.size(); i++) {

                Vector<Feature> fs1 = calculateSIFT(listImages.get(i));
                listData.add(new InstFeatures(fs1, 3, listImages.get(i).getTitle()));
            }
        }
        long start_time = System.currentTimeMillis();
        for (int j = 0; j < listDirClass.size(); j++) {
            int cl;
            if (listDirClass.get(j).getName().contains("neur")) {
                cl = 1;//"N";
            } else if (listDirClass.get(j).getName().contains("astro")) {
                cl = 3;// "A";
            } else {
                cl = 2;// "B";
            }
            ArrayList<ImagePlus> listImages = getImagesFolder(listDirClass.get(j).getAbsolutePath());
            IJ.log("one folder...");
            for (int i = 0; i < listImages.size(); i++) {

                Vector<Feature> fs1 = calculateSIFT(listImages.get(i));
                listData.add(new InstFeatures(fs1, cl, listImages.get(i).getTitle()));
            }
        }
        IJ.log("Images with SIFT features saved");
        IJ.log("To calculate SIFT features took " + (System.currentTimeMillis() - start_time) + "ms");
        return listData;
    }

    protected ArrayList<ImagePlus> getImagesFolder(String path) {
        ArrayList<ImagePlus> all_imag = new ArrayList<ImagePlus>();

        File directory = new File(path);
        String[] filesDir = directory.list();
        int nFiles = filesDir.length;
        if (nFiles != 0) {
            for (int i = 0; i < filesDir.length; i++) {
                if (filesDir[i].contains(".tif")) {
                    all_imag.add(new ImagePlus(path + File.separator + filesDir[i]));
                }
            }
        }
        return all_imag;
    }

    protected ArrayList<File> getClassFolder(String path) {
        ArrayList<File> all_folder = new ArrayList<File>();
        File directory = new File(path);
        String[] filesDir = directory.list();
        int nFiles = filesDir.length;
        if (nFiles != 0) {
            for (int i = 0; i < filesDir.length; i++) {
                File dir = new File(path + File.separator + filesDir[i]);
                if (dir.isDirectory()) {
                    all_folder.add(dir);
                }
            }
        }
        return all_folder;
    }

    //to extract the instances to arff-file.
    public void extractArffClustering(Instances dataSet, String name) {
        ArffSaver saver = new ArffSaver();
        saver.setInstances(dataSet);
        try {
            saver.setFile(new File(output_path + File.separator + name + ".arff"));
            saver.writeBatch();
        } catch (Exception ex) {
            IJ.log(ex.getMessage());
        }
    }

    /*
     code from http://fly.mpi-cbg.de/~saalfeld/Projects/javasift.html
     */
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

    /**
     * This method is a bit different to the same method in the class called
     * Sift_model.java
     *
     * @param listData
     * @param ngroups
     * @return
     */
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
                allF_Class.add(new OneFeature(null, listData.get(i).getCl(), listData.get(i).getName()));
            }
            for (Feature f : listData.get(i).getFs1()) {
                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(n), f.descriptor[n]);
                }
                ins.setValue((Attribute) fvWekaAttributes.elementAt(128), String.valueOf(listData.get(i).getCl()));
                ins_data.add(ins);

                allF_Class.add(new OneFeature(f, listData.get(i).getCl(), listData.get(i).getName()));
            }
        }
        return ins_data;
    }
}
