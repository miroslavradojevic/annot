package Sift;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
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
import weka.classifiers.bayes.NaiveBayes;
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
 */
public class Sift_model implements PlugIn {

    String path;//where are the images for studying
    String output_path;//folder to save the models and the arff files
    String expName; //name of experiment (or mosaic)
    int ngroups = 20; //number of groups for "Bag of the words"

    //variables for SKM-Algorithm (for training)
    /*
     listData: Each element represented one patch, and it has its name, 
     the kind of patch (neurons==0, background ==1 and astroccyte==2). Also it has 
     a vector with all its features (each feature has a 128 descriptors).
     */
    ArrayList<InstFeatures> listData = new ArrayList<InstFeatures>();
    /*
     allF: each element is a feature of an image. All features from all patches are mixed here. 
     Each element has the name of patch which belongs to, also it has the feature with its 128 descriptors,
     the real kind of class which is the patch and the predict kind of class. 
     */
    ArrayList<OneFeature> allF = new ArrayList<OneFeature>();

    //parameters to calculate SIFT features (with JavaSIFT)
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

        //-----DIALOG-----
        path = Prefs.get("annot.input_folder", System.getProperty("user.home"));
        output_path = Prefs.get("annot.output_folder", System.getProperty("user.home"));
        expName = Prefs.get("annot.expName", "m01");

        GenericDialog gdG = new GenericDialog("SIFT Model - Weka");
        gdG.addStringField("input_folder", path, 80);
        gdG.addStringField("output_folder", output_path, 80);
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
        path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.input_folder", path);
        output_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.output_folder", output_path);
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

        if (path.isEmpty() || output_path.isEmpty()) {
            return;
        }
        long start_time = System.currentTimeMillis();
        //----- FIRST STEP: TRAINING -----

        //generate the SIFTFeatures
        listData = getSIFTFeatures(path);

        IJ.log("Weka clustering (SKM):");
        //generate the instances for Weka and fill allF
        Instances ins_data = getInstancesClustering(listData, ngroups);
        IJ.log("Generating arff-file...");
        extractArffClustering(ins_data, expName);
        try {
            allF = wekaSKM(ins_data, allF);
            //in allF, the cluster for codewords per each feature is there
        } catch (Exception ex) {
            Logger.getLogger(Sift_model.class.getName()).log(Level.SEVERE, null, ex);
            IJ.log(ex.getMessage());
        }
        //to calculate the histograms per each patch
        ArrayList<InstHistograms> instHisto = getHistograms(allF, ngroups);
        //generate the instances for Weka and fill AllF
        Instances ins_data_histo = getInstancesHistogram(instHisto);
        extractArffClustering(ins_data_histo, expName + "_histo");
        try {
            wekaNaiveBayes(ins_data_histo, instHisto);
        } catch (Exception ex) {
            Logger.getLogger(Sift_model.class.getName()).log(Level.SEVERE, null, ex);
            IJ.log(ex.getMessage());
        }
        IJ.log("This method took " + (System.currentTimeMillis() - start_time) + "ms");
        IJ.log("The model-files have been generated. Now you can use it to analyze other images.");

    }

    /**
     *
     * @param ins_data
     * @param data_H
     * @throws Exception
     */
    protected void wekaNaiveBayes(Instances ins_data, ArrayList<InstHistograms> data_H) throws Exception {

        // Create a naive bayes classifier
        Classifier cModel = (Classifier) new NaiveBayes();
        cModel.buildClassifier(ins_data);

        weka.core.SerializationHelper.write(output_path + "\\" + expName + "_model_NB" + ".model", cModel);

        IJ.log("the model has been saved in " + output_path + "\\model_NB" + ".model");
    }

    /**
     *
     * @param ins_data
     * @param allF
     * @return
     * @throws Exception
     */
    protected ArrayList<OneFeature> wekaSKM(Instances ins_data, ArrayList<OneFeature> allF) throws Exception {
        ArrayList<Integer> listclusters = new ArrayList<Integer>();

        //the instance has 129 attributes: 128 descriptors plus one attribute more for the class
        //this filter removes the attribute class
        weka.filters.unsupervised.attribute.Remove filter = new Remove();
        filter.setAttributeIndices("129");
        filter.setInputFormat(ins_data);
        Instances data_without_class = weka.filters.Filter.useFilter(ins_data, filter);

        SimpleKMeans skm = new SimpleKMeans();
        skm.setNumClusters(ngroups);
        skm.setSeed(42);//this seed is by default
        skm.buildClusterer(data_without_class);
        /*
         to save a model-file. I tried with other three ways more for saving the files, 
         but I didn't get that later, the file is opened with Weka interfaz, but the file looks like ok 
         and from the code works right.
         */
        weka.core.SerializationHelper.write(output_path + "\\" + expName + "_model_SKM" + ".model", skm);

        for (int i = 0; i < data_without_class.size(); i++) {
            int cluster = skm.clusterInstance(data_without_class.get(i));
            listclusters.add(cluster);
            allF.get(i).setCluster(cluster);
        }
        IJ.log("the model, calculates with SKM(for BoW), has been saved in " + output_path + "\\" + expName + "_model_SKM" + ".model");
        return allF;
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
        IJ.log("number of histograms: " + String.valueOf(instHist.size())); //it has to be the same number that the patches
        return instHist;
    }

    /**
     * code from http://fly.mpi-cbg.de/~saalfeld/Projects/javasift.html
     *
     * @param path where the patches for studying are.
     * @return a list with sift-features per each patch
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

    protected Instances getInstancesHistogram(ArrayList<InstHistograms> instHisto) {
        String[] names = new String[ngroups];
        for (int k = 0; k < ngroups; k++) {
            names[k] = "group_" + k;
        }
        IJ.log("Generating model to classify...");
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
            ins.setValue((Attribute) fvWekaAttributes.elementAt(ngroups), String.valueOf(instHisto.get(i).getCl()));
            ins_data_histo.add(ins);
        }
        return ins_data_histo;
    }

    /**
     *
     * @param listData
     * @param ngroups
     * @param isTrain is is training, this parameter has to be "true", in other
     * case, "false".
     * @return the instance to be able to work with Weka.
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
        ins_data.setClassIndex(names.length);
        // We create the instances and add them to the set of instances
        for (int i = 0; i < nrows; i++) {
            Instance ins = new DenseInstance(ncolumns);
            if (listData.get(i).getFs1().isEmpty()) { //to initialize
                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(n), 0);
                }

                ins.setValue((Attribute) fvWekaAttributes.elementAt(128), String.valueOf(listData.get(i).getCl()));
                ins_data.add(ins);
                allF.add(new OneFeature(null, listData.get(i).getCl(), listData.get(i).getName()));

            }
            for (Feature f : listData.get(i).getFs1()) {

                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(n), f.descriptor[n]);
                }
                ins.setValue((Attribute) fvWekaAttributes.elementAt(128), String.valueOf(listData.get(i).getCl()));
                ins_data.add(ins);

                allF.add(new OneFeature(f, listData.get(i).getCl(), listData.get(i).getName()));
            }
        }
        return ins_data;
    }
}
