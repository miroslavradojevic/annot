/*
 * Extract from these classes http://fly.mpi-cbg.de/~saalfeld/Projects/javasift.html of  Stephan Saalfeld <saalfeld@mpi-cbg.de>
 */
package patch;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Polygon;
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
import weka.classifiers.lazy.IBk;
import weka.clusterers.SimpleKMeans;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 *
 * @author Gadea
 */
public class SiftWeka implements PlugIn {

    String path;// = "C:\\Users\\Gadea\\Desktop\\m01";//where the images are
    String output_path;// = "C:\\Users\\Gadea\\Desktop\\output";
    ArrayList<instFeatures> listData = new ArrayList<instFeatures>();
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
    //------------------
    @Override
    public void run(String arg) {

        path = Prefs.get("annot.input_folder", System.getProperty("user.home"));
        output_path = Prefs.get("annot.output_folder", System.getProperty("user.home"));

        GenericDialog gdG = new GenericDialog("SIFT - Weka");
        gdG.addStringField("input_folder", path, 80);
        gdG.addStringField("output_folder", output_path, 80);
        gdG.addMessage("--- to calculate SIFT features ---");
        gdG.addNumericField("steps_per_scale_octave :", steps, 0);
        gdG.addNumericField("initial_gaussian_blur :", initial_sigma, 2);
        gdG.addNumericField("feature_descriptor_size :", fdsize, 0);
        gdG.addNumericField("feature_descriptor_orientation_bins :", fdbins, 0);
        gdG.addNumericField("minimum_image_size :", min_size, 0);
        gdG.addNumericField("maximum_image_size :", max_size, 0);
        gdG.addCheckbox("upscale_image_first", upscale);
        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }
        path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.input_folder", path);
        output_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.output_folder", output_path);
        steps = (int) gdG.getNextNumber();
        initial_sigma = (float) gdG.getNextNumber();
        fdsize = (int) gdG.getNextNumber();
        fdbins = (int) gdG.getNextNumber();
        min_size = (int) gdG.getNextNumber();
        max_size = (int) gdG.getNextNumber();
        upscale = gdG.getNextBoolean();
        if (upscale) {
            scale = 2.0f;
        }
        if (path.isEmpty() || output_path.isEmpty()) {
            return;
        }
        ArrayList<File> listDirClass = getClassFolder(path);
        if(listDirClass.isEmpty())
        {
            IJ.log("no subfolders");
            return;
        }
        long start_time = System.currentTimeMillis();
        for (int j = 0; j < listDirClass.size(); j++) {
            int cl;
            if (listDirClass.get(j).getName().contains("neur")) {
                cl = 1;//"N";
            } else if (listDirClass.get(j).getName().contains("astro")) {
                cl = 2;// "A";
            } else {
                cl = 3;// "B";
            }
            ArrayList<ImagePlus> listImages = getImagesFolder(listDirClass.get(j).getAbsolutePath());
            IJ.log("one folder...");
            for (int i = 0; i < listImages.size(); i++) {
                Vector<Feature> fs1 = calculateSIFT(listImages.get(i));
                listData.add(new instFeatures(fs1, cl));
            }
        }
        IJ.log("Images with SIFT features saved");
        IJ.log("To calculate SIFT features took " + (System.currentTimeMillis() - start_time) + "ms");
        IJ.log("Weka...");
        long second_time = System.currentTimeMillis();
        String[] names = getNames();
        try {
            runWeka(names, listData);
            IJ.log("To work with Weka took " + (System.currentTimeMillis() - second_time) + "ms");
        } catch (Exception ex) {
            IJ.log("exception " + ex.getMessage());
            Logger.getLogger(SiftWeka.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * To generate the instances which are necessary to work with Weka
     */
    protected String[] getNames() {
        String[] names = new String[133];
        names[0] = "X";
        names[1] = "Y";
        names[2] = "orientation";
        names[3] = "scale";
        int n = 4;
        for (int k = 0; k < 128; k++) {
            names[n] = "desccriptor_" + k;
            n++;
        }
        names[n] = "class";

        return names;
    }

    /*
     * Unsupervised Method: it use simple k-means algorithm
     */
    public void runWekaClustering(String[] names, ArrayList<instFeatures> listdata) throws Exception {
        int ncolumns = names.length - 1;//numero de caracteristicas
        int nrows = listdata.size();//numero de instancias

        // We create a feature vector and the attributes to the feature vector
        FastVector fvWekaAttributes = new FastVector(ncolumns);

        for (int i = 0; i < ncolumns; i++) {
            fvWekaAttributes.addElement(new Attribute(names[i]));
        }

        // We create an empty set of instances
        Instances ins_data = new Instances("Rel", fvWekaAttributes, ncolumns);

        // We create the instances and add them to the set of instances
        for (int i = 0; i < nrows; i++) {
            Instance ins = new DenseInstance(ncolumns);
            //OJO con los indices al hacer las pruebas
            for (Feature f : listdata.get(i).getFs1()) {
                ins.setValue((Attribute) fvWekaAttributes.elementAt(0), f.location[0]);
                ins.setValue((Attribute) fvWekaAttributes.elementAt(1), f.location[1]);
                ins.setValue((Attribute) fvWekaAttributes.elementAt(2), f.orientation);
                ins.setValue((Attribute) fvWekaAttributes.elementAt(3), f.scale);
                int k = 4;
                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(k), f.descriptor[n]);
                    k++;
                }
//            ins.setClassValue(listdata.get(i).getCl());
                ins_data.add(ins);
            }
        }

        ArrayList<Integer> listclusters = new ArrayList<Integer>();
        SimpleKMeans skm = new SimpleKMeans();
        skm.setNumClusters(2);
        skm.setSeed(42);
        skm.buildClusterer(ins_data);

        for (Instance i : ins_data) {
            int cluster = skm.clusterInstance(i);
            listclusters.add(cluster);
        }
        for (int i = 0; i < listclusters.size(); i++) {
            IJ.log(String.valueOf(listclusters.get(i)));
        }
        IJ.log("classified!");
    }

    /*
     * Supervised Method: it used k-nearest neighbord (knn) algorithm
     */
    public void runWeka(String[] names, ArrayList<instFeatures> listdata) throws Exception {
        int ncolumns = names.length;//number or features for Weka
        int nrows = listdata.size();//number of instances

        // We create a feature vector and the attributes to the feature vector
        FastVector fvWekaAttributes = new FastVector(ncolumns);

        for (int i = 0; i < ncolumns; i++) {
            fvWekaAttributes.addElement(new Attribute(names[i]));
        }

        // We create an empty set of instances
        Instances ins_data = new Instances("Rel", fvWekaAttributes, ncolumns);

        // We create the instances and add them to the set of instances
        for (int i = 0; i < nrows; i++) {
            Instance ins = new DenseInstance(ncolumns);
            for (Feature f : listdata.get(i).getFs1()) {
                ins.setValue((Attribute) fvWekaAttributes.elementAt(0), f.location[0]);
                ins.setValue((Attribute) fvWekaAttributes.elementAt(1), f.location[1]);
                ins.setValue((Attribute) fvWekaAttributes.elementAt(2), f.orientation);
                ins.setValue((Attribute) fvWekaAttributes.elementAt(3), f.scale);
                int k = 4;
                for (int n = 0; n < 128; n++) {
                    ins.setValue((Attribute) fvWekaAttributes.elementAt(k), f.descriptor[n]);
                    k++;
                }
                ins.setValue((Attribute) fvWekaAttributes.elementAt(k), listdata.get(i).getCl());
                ins_data.add(ins);
            }
        }
        ins_data.setClassIndex(ins_data.numAttributes() - 1);

        /**
         * Training with the previous data and "first and second" are classified
         * (that is a example of classification with two patches)
         */
        //do not use first and second
        Instance first = ins_data.instance(0);
        Instance second = ins_data.instance(1);
        ins_data.delete(0);
        ins_data.delete(1);

        Classifier ibk = new IBk();
        ibk.buildClassifier(ins_data);

        double class1 = ibk.classifyInstance(first);
        double class2 = ibk.classifyInstance(second);

        IJ.log("first: " + class1 + "\nsecond: " + class2);
        IJ.log("1== neuron ; 2== astrocyte ; 3== background");
        IJ.log("classified!");
    }

    /*
    *to get the images
    */
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

    /*
    * to get the subfolders 
    */
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

    protected static void drawSquare(ImageProcessor ip, double[] o, double scale, double orient) {
        scale /= 2;

        double sin = Math.sin(orient);
        double cos = Math.cos(orient);

        int[] x = new int[6];
        int[] y = new int[6];

        x[0] = (int) (o[0] + (sin - cos) * scale);
        y[0] = (int) (o[1] - (sin + cos) * scale);

        x[1] = (int) o[0];
        y[1] = (int) o[1];

        x[2] = (int) (o[0] + (sin + cos) * scale);
        y[2] = (int) (o[1] + (sin - cos) * scale);
        x[3] = (int) (o[0] - (sin - cos) * scale);
        y[3] = (int) (o[1] + (sin + cos) * scale);
        x[4] = (int) (o[0] - (sin + cos) * scale);
        y[4] = (int) (o[1] - (sin - cos) * scale);
        x[5] = x[0];
        y[5] = y[0];

        ip.drawPolygon(new Polygon(x, y, x.length));
    }

    /*
    *To calculate the SIFT features with these classes http://fly.mpi-cbg.de/~saalfeld/Projects/javasift.html
    */
    protected Vector<Feature> calculateSIFT(ImagePlus imp) {
//        long start_time = System.currentTimeMillis();
        ImageProcessor ip1 = imp.getProcessor().convertToFloat();
        ImageProcessor ip2 = imp.getProcessor().duplicate().convertToRGB();

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
//            long start_time = System.currentTimeMillis();
//                System.out.println("processing SIFT ...");
        sift.init(fa, steps, initial_sigma, min_size, max_size);
        fs1 = sift.run(max_size);
        Collections.sort(fs1);
//            IJ.log(" took " + (System.currentTimeMillis() - start_time) + "ms");

//            IJ.log("in one patche: "+fs1.size() + " features identified and processed");
        ip2.setLineWidth(1);
        ip2.setColor(Color.red);
        for (Feature f : fs1) {
//                IJ.log(f.location[0] + " " + f.location[1] + " " + f.scale + " " + f.orientation);
            drawSquare(ip2, new double[]{f.location[0] / scale, f.location[1] / scale}, fdsize * 4.0 * (double) f.scale / scale, (double) f.orientation);
        }
        ImagePlus imp1 = new ImagePlus(imp.getTitle() + " Features ", ip2);
//            imp1.show();
        File file = new File(output_path);
        if (!file.exists()) {
            file.mkdirs();
        }
        FileSaver fsMosaic = new FileSaver(imp1);
        String nameImg = file.getAbsolutePath() + File.separator + imp.getShortTitle() + "_SIFT.tif";
        fsMosaic.saveAsTiff(nameImg);
//        IJ.log(" Calculating one patch took " + (System.currentTimeMillis() - start_time) + "ms");
        return fs1;
    }
}
