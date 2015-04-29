/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package patch.check;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Gadea
 */
public class Read_tsv implements PlugIn {

    private static String dirFile;//path of tsv-file 
    String image_path;//path of mosaic image
    String annot_file_path;//path the annotation file, it can be log-file or zip-file 
    int D = 500; //dimension of the square
    double minArea = 0; //minimum area for overlapping
    int areaP = 20; //min porcentage for overlapping 
    String output_path; //path to save the results

    @Override
    public void run(String string) {

        //-----DIALOG-----
        dirFile = Prefs.get("readTSV.destination_folder", System.getProperty("user.home"));
        image_path = Prefs.get("readTSV.image_path", System.getProperty("user.home"));
        annot_file_path = Prefs.get("readTSV.annot_path", System.getProperty("user.home"));
        output_path = Prefs.get("readTSV.output_path", System.getProperty("user.home"));
        areaP = (int) Prefs.get("readTSV.porcArea", 20);
        D = (int) Prefs.get("readTSV.D", 500);
        final String[] types = new String[]{".zip", ".log"};

        GenericDialog gdG = new GenericDialog("MosaicClassify");
        gdG.addStringField("tsv_file", dirFile, 80);
        gdG.addStringField("mosaic_path", image_path, 80);
        gdG.addChoice("type file of the annotations:", types, types[0]);
        gdG.addStringField("annotation", annot_file_path, 80);
        gdG.addStringField("annotation", output_path, 80);
        gdG.addNumericField("%_overlapped", areaP, 0);
        gdG.addNumericField("size_patch", D, 0);

        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }

        dirFile = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("readTSV.destination_folder", dirFile);
        image_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("readTSV.image_path", image_path);
        int itype = gdG.getNextChoiceIndex();
        annot_file_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("readTSV.annot_path", annot_file_path);
        output_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("readTSV.output_path", output_path);
        areaP = (int) gdG.getNextNumber();
        Prefs.get("readTSV.porcArea", areaP);
        D = (int) gdG.getNextNumber();
        Prefs.get("readTSV.D", D);

        ImagePlus inimg = new ImagePlus(image_path);

        File pathFile;
        FileReader fr;
        BufferedReader br;
        pathFile = new File(dirFile);

        ArrayList<String> classPredict = new ArrayList<String>();
        ArrayList<Point> patches = new ArrayList<Point>();

        ArrayList<Rectangle> NeuronList = new ArrayList<Rectangle>();

        minArea = D * D * areaP / 100;

        //read tsv-file
        try {
            fr = new FileReader(pathFile);

            br = new BufferedReader(fr);
            while(!br.readLine().contains("Image No.")){
                
            }
            String line = br.readLine();
            if (line.contains("<tr><td>1</td>")) {
                while ((line != null)) {
                    String[] aux = line.trim().split("<td>");
                    for (int j = 1; j < aux.length; j++) {
                        if (aux[j].equals("Neuron</td>")) {
                            classPredict.add("Neuron");
                        } else if (aux[j].equals("noNeuron</td>")) {
                            classPredict.add("noNeuron");
                        } else if (aux[j].contains("A HREF")) {
                            String[] aux2 = aux[j].trim().split("\"");
                            File img = new File(aux2[1]);
                            String nameImg = img.getName();
                            String[] aux3 = nameImg.trim().split(",");
                            Point p = new Point(Integer.parseInt(aux3[5]), Integer.parseInt(aux3[6]));
                            patches.add(p);
                        }
                    }
                    line = br.readLine();
                }
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Read_tsv.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Read_tsv.class.getName()).log(Level.SEVERE, null, ex);
        }

        int P = 0, total = 0, PClassify = 0, totalClassify;
        //save the patches which are neurons in the classification
        for (int i = 0; i < patches.size(); i++) {
            if (classPredict.get(i).equals("Neuron")) {
                NeuronList.add(new Rectangle((int) patches.get(i).getX(), (int) patches.get(i).getY(), D, D));
                PClassify++;
            }
        }
        totalClassify = patches.size();
        Rectangle[] Neurons_annot;
        if (itype == 1) {
            //to read annot-file (.log)
            ArrayList<String> type_annot = new ArrayList<String>();  // mosaic name
            ArrayList<Point> points_annot = new ArrayList<Point>();
            //D is the same for all
            try {
                String line;
                fr = new FileReader(annot_file_path);
                br = new BufferedReader(fr);

                br.readLine(); //to read the comment
                while ((line = br.readLine()) != null) {
                    String[] aux = line.split("\t", 5);//[0] TYPE, [1] x, [2] y, [3] D, [4] D
                    if (aux[0].equals("Neuron")) { //it only reads the neurons
                        type_annot.add(aux[0]);
                        points_annot.add(new Point(Integer.parseInt(aux[1]), Integer.parseInt(aux[2])));
                        P++;
                    }
                    total++;
                }
            } catch (IOException ex) {
                System.out.println("ERROR: " + ex.getMessage());
            }
            //only neurons from annot_.log
            Neurons_annot = new Rectangle[points_annot.size()];

            for (int i = 0; i < points_annot.size(); i++) {
                String actual_class = type_annot.get(i);
                if (actual_class.equals("Neuron")) {
                    Neurons_annot[i] = new Rectangle((int) points_annot.get(i).getX(), (int) points_annot.get(i).getY(), D, D);
                }
            }
        } else {
            //to read zip-file
            IJ.open(annot_file_path);
            RoiManager rm = RoiManager.getInstance();
            if (rm == null) {
                rm = new RoiManager();
            }
            Neurons_annot = new Rectangle[rm.getCount()];

            Hashtable<String, Roi> table = (Hashtable<String, Roi>) rm.getROIs();
           
            for (String label : table.keySet()) {
                Roi roi = table.get(label);
                Neurons_annot[P] = roi.getBounds();
                P++;
            }
            rm.close();
        }
        //to compare "neurons" (classification) with "neurons" (annot.log (or zip)) 
        Rectangle[] NClass = new Rectangle[NeuronList.size()];
        Rectangle[] NAnnot = new Rectangle[NeuronList.size()];
        Double[] NOver = new Double[NeuronList.size()];

        for (int i = 0; i < NeuronList.size(); i++) {
            //maybe one neuron classified overloaps with 2 or more neurons from annot
            ArrayList<Rectangle> auxNeurons = new ArrayList<Rectangle>();
            ArrayList<Double> auxPorcOver = new ArrayList<Double>();
            NClass[i] = NeuronList.get(i);
            for (int j = 0; j < Neurons_annot.length; j++) {
                double over = overlap(NeuronList.get(i), Neurons_annot[j]);
                if (over > minArea) {
                    auxNeurons.add(Neurons_annot[j]);
                    auxPorcOver.add(over);
                }
            }
            if (!auxNeurons.isEmpty()) {
                double max = auxPorcOver.get(0);
                NOver[i] = max;
                NAnnot[i] = auxNeurons.get(0);
                for (int j = 1; j < auxNeurons.size(); j++) {
                    if (max < auxPorcOver.get(j)) {
                        max = auxPorcOver.get(j);
                        NOver[i] = max;
                        NAnnot[i] = auxNeurons.get(j);
                    }
                }
            } else {
                NOver[i] = -1d;
                NAnnot[i] = null;
            }
        }
        //here more than one neuron_annot may be repeated (different NClass have the same NAnnot)
        ArrayList<Rectangle> TP_neurons = new ArrayList<Rectangle>();
        ArrayList<Rectangle> FP_neurons = new ArrayList<Rectangle>();
        ArrayList<Rectangle> TP_annot_neurons = new ArrayList<Rectangle>();
        ArrayList<Double> TP_over = new ArrayList<Double>();
        for (int i = 0; i < NAnnot.length; i++) {
            for (int j = 0; j < NAnnot.length; j++) {
                if ((j != i) && (NAnnot[i] != null) && NAnnot[j] != null && (NAnnot[i] == NAnnot[j])) {
                    if (NOver[i] <= NOver[j]) {
                        NOver[i] = -1d;
                        NAnnot[i] = null;
                    } else {
                        NOver[j] = -1d;
                        NAnnot[j] = null;
                    }
                }
            }
        }
        for (int i = 0; i < NClass.length; i++) {
            if (NOver[i] != -1d && NAnnot[i] != null) {
                TP_neurons.add(NClass[i]);
                TP_annot_neurons.add(NAnnot[i]);
                TP_over.add(NOver[i]);
            } else {
                FP_neurons.add(NClass[i]);
            }
        }
        int TP, FN, FP;
        TP = TP_neurons.size();
        FP = FP_neurons.size();

        //FN is the number of neurons in annot minus the number of neurons found.
        FN = P - TP;

        System.out.println("total patches in classification: " + totalClassify);
        System.out.println("P (Neurons) in annot: " + P);
        System.out.println("P' (Neurons) in classification: " + PClassify);
        System.out.println("TP: " + TP + " (in red)");
        System.out.println("FN: " + FN + " (in yellow)");
        System.out.println("FP: " + FP + " (in blue)");

        double TPR = (double) TP / (TP + FN); //recall
        double PPV = (double) TP / (TP + FP); //precision

        System.out.println("TPR (Recall): " + TPR);
        System.out.println("PPV (Precision): " + PPV);

        System.out.println("------------------------");
        for (int i = 0; i < TP_neurons.size(); i++) {
            double auxOver = TP_over.get(i) / (double) (D * D);
            System.out.println(TP_neurons.get(i).getX() + "-" + TP_neurons.get(i).getY() + "\t" + " % cover area " + auxOver);
        }

        //to visualizate the results
        Overlay curr_ovl = new Overlay();
        for (Rectangle TP_neuron : TP_neurons) {
            //in red the patches are and have been classified like neurons
            Roi roi_to_add = new Roi(TP_neuron);
            roi_to_add.setFillColor(new Color(1, 0, 0, 0.2f));
            roi_to_add.setStrokeColor(new Color(1, 0, 0, 1));
            curr_ovl.add(roi_to_add);
        }
        for (Rectangle FP_neuron : FP_neurons) {
            //in blue the patches have been classified like neurons but they are not
            Roi roi_to_add = new Roi(FP_neuron);
            roi_to_add.setFillColor(new Color(0, 0, 1, 0.2f));
            roi_to_add.setStrokeColor(new Color(0, 0, 1, 1));
            curr_ovl.add(roi_to_add);
        }
        for (Rectangle TP_original : TP_annot_neurons) {
            //it paints the original patch in case of neuron
            Roi roi_to_add = new Roi(TP_original);
            roi_to_add.setStrokeColor(Color.WHITE);
            curr_ovl.add(roi_to_add);
        }
        for (Rectangle Nannot : Neurons_annot) {
            //it paints the yellow patch the neurons which have not been found
            if (!TP_annot_neurons.contains(Nannot)) {
                Roi roi_to_add = new Roi(Nannot);
                roi_to_add.setStrokeColor(Color.YELLOW);
                curr_ovl.add(roi_to_add);
            }
        }

        ImagePlus imout = inimg.duplicate();
        imout.setOverlay(curr_ovl);
        imout.show();
        imout.updateAndDraw();

        File f = new File(output_path);
        if (!f.exists()) {
            f.mkdirs();
        }

        FileSaver fsMosaic = new FileSaver(imout);
        String nameMosaic = f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_log.tif";
        fsMosaic.saveAsTiff(nameMosaic);
        FileSaver fsMosaicPNG = new FileSaver(imout);
        nameMosaic = f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_log.png";
        fsMosaicPNG.saveAsPng(nameMosaic);
    }

    private double overlap(Rectangle rect1, Rectangle rect2) {

        double x11 = rect1.getX();//left -up
        double y11 = rect1.getY();//left -up

        double x12 = rect1.getX() + rect1.getWidth();//right -up

        double y12 = rect1.getY() + rect1.getHeight();//left-down

        double x21 = rect2.getX();//left -up
        double y21 = rect2.getY();//left -up

        double x22 = rect2.getX() + rect2.getWidth();//right -up

        double y22 = rect2.getY() + rect2.getHeight();//left-down

        double xDiff;
        if (x11 < x21) {
            xDiff = x12 - x21;
        } else {
            xDiff = x22 - x11;
        }

        double yDiff;
        if (y11 < y21) {
            yDiff = y12 - y21;
        } else {
            yDiff = y22 - y11;
        }
        xDiff = (xDiff < 0) ? 0 : xDiff;
        yDiff = (yDiff < 0) ? 0 : yDiff;

        return xDiff * yDiff;
    }

}
