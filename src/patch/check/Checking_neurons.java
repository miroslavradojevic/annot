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
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 *
 * @author Gadea
 */
public class Checking_neurons implements PlugIn {

    String path, annot_file_path, image_path; //image_path
    double areaP = 30; //minimum porcentage of overlapping area
    double minArea = 0;

    @Override
    public void run(String string) {

        //----DIALOG----
        path = Prefs.get("annot.destination_folder", System.getProperty("user.home")); //the path where the patches are
        image_path = Prefs.get("annot.image_path", System.getProperty("user.home")); //the path and name the original mosaic image
        annot_file_path = Prefs.get("annot.annot_path", System.getProperty("user.home")); //the path with the annotation file, this plugin works with the annotation in zip-file

        GenericDialog gdG = new GenericDialog("Checking Neurons");
        gdG.addStringField("patches_dir", path, 80);
        gdG.addStringField("mosaic_path", image_path, 80);
        gdG.addStringField("annotation (zip-file)", annot_file_path, 80);
        gdG.addNumericField("min area of overlapping ", areaP, 0);

        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }

        path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.destination_folder", path);
        image_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.image_path", image_path);
        annot_file_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.annot_path", annot_file_path);
        areaP = gdG.getNextNumber();
        Prefs.set("annot.minArea", areaP);

        if (path.isEmpty() || annot_file_path.isEmpty()) {
            return;
        }

        ImagePlus inimg = new ImagePlus(image_path);
        if (inimg == null) {
            return;
        }
        //to read from the patches, it needs to get the coordenates per each patch, they are stored in a list (patches_detec)
        int D = 0;
        int x = 0;
        int y = 0;
        ArrayList<Rectangle> patches_detec = new ArrayList<Rectangle>();
        File directory = new File(path);
        String[] filesDir = directory.list();
        int nFiles = filesDir.length;
        for (int i = 0; i < nFiles; i++) {
            if (filesDir[i].contains(".tif")) {
                String[] auxName = filesDir[i].trim().split(",");
                D = Integer.valueOf(auxName[7]);
                x = Integer.valueOf(auxName[5]);
                y = Integer.valueOf(auxName[6]);
                patches_detec.add(new Rectangle(x, y, D, D));
            }
        }
        //to read annot-file
        /**
         * In the case that it needs to work with log-file, it can use this
         * code:
         *
         * ArrayList<String> type_annot = new ArrayList<String>(); // mosaic
         * name ArrayList<Point> points_annot = new ArrayList<Point>(); //D is
         * the same for all
         *
         * //to read annot file (.log) try { FileReader fr; BufferedReader br;
         * String line; fr = new FileReader(annot_file_path); br = new
         * BufferedReader(fr);
         *
         * br.readLine(); //to read the comment while ((line = br.readLine()) !=
         * null) { String[] aux = line.split("\t", 5);//[0] TYPE, [1] x, [2] y,
         * [3] D, [4] D if (aux[0].contains("NEUR")) { //it only reads the
         * neurons type_annot.add(aux[0]); points_annot.add(new
         * Point(Integer.parseInt(aux[1]), Integer.parseInt(aux[2]))); } } }
         * catch (IOException ex) { System.out.println("ERROR: " +
         * ex.getMessage()); } //only neurons from annot_.log Rectangle[]
         * Neurons_annot = new Rectangle[points_annot.size()];
         *
         * for (int i = 0; i < points_annot.size(); i++) { String actual_class =
         * type_annot.get(i); if (actual_class.contains("NEU")) {
         * Neurons_annot[i] = new Rectangle((int) points_annot.get(i).getX(),
         * (int) points_annot.get(i).getY(), D, D); } }*
         */

        //to read zip-file and it stores the neuron squares in a list (Neurons_annot)
        IJ.open(annot_file_path);
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) {
            rm = new RoiManager();
        }
        Rectangle[] Neurons_annot = new Rectangle[rm.getCount()];

        Hashtable<String, Roi> table
                = (Hashtable<String, Roi>) rm.getROIs();
        int k = 0;
        for (String label : table.keySet()) {
            Roi roi = table.get(label);
            Neurons_annot[k] = roi.getBounds();
            k++;
        }
        rm.close();

        Rectangle[] Neurons_found = new Rectangle[Neurons_annot.length];
        //it compares the squares in zip-file with the squares which has been detected previously
        for (int i = 0; i < Neurons_annot.length; i++) {
            ArrayList<Rectangle> auxOver = new ArrayList<Rectangle>();
            ArrayList<Double> auxValueOver = new ArrayList<Double>();
            for (int j = 0; j < patches_detec.size(); j++) {
                double over = overlap(Neurons_annot[i], patches_detec.get(j));
                if (over > minArea) {
                    auxOver.add(patches_detec.get(j));
                    auxValueOver.add(over);
                }
            }
            //if there are several squares that are overlapping to the same square, it's selected the square with more overlapped area has.
            if (auxOver.isEmpty()) {
                Neurons_found[i] = null;
            } else {
                double max = auxValueOver.get(0);
                int index = 0;
                for (int j = 1; j < auxValueOver.size(); j++) {
                    if (max < auxValueOver.get(j)) {
                        max = auxValueOver.get(j);
                        index = j;
                    }
                }
                Neurons_found[i] = auxOver.get(index);
            }
        }

        int Nannot = Neurons_annot.length; //number the neurons in annot (zip) file
        int lclmax = patches_detec.size();//number the lcl maxima points have been detected

        //the neurons have been found, are stored in a list (neuronsF)
        ArrayList<Rectangle> neuronsF = new ArrayList<Rectangle>();
        for (int i = 0; i < Neurons_found.length; i++) {
            if (Neurons_found[i] != null) {
                neuronsF.add(Neurons_found[i]);
            }
        }
        int count = neuronsF.size(); //number the "neurons" have been found between the patches from lcl maxima points

        System.out.println(Nannot + " n of neurons in annot-file");
        System.out.println(lclmax + " n of patches found with local maxima");
        System.out.println(count + " n of neurons in the patches lcl max");

        //to draw the results
        Overlay curr_ovl = new Overlay();
        for (int i = 0; i < neuronsF.size(); i++) {
            //in red the patches are and have been classified like neurons
            Roi roi_to_add = new Roi(neuronsF.get(i));
            roi_to_add.setFillColor(new Color(1, 0, 0, 0.2f));
            roi_to_add.setStrokeColor(new Color(1, 0, 0, 1));
            curr_ovl.add(roi_to_add);
        }
        for (Rectangle neuron : Neurons_annot) {
            //it paints the original patch in case of neuron
            Roi roi_to_add = new Roi(neuron);
            roi_to_add.setStrokeColor(Color.WHITE);
            curr_ovl.add(roi_to_add);
        }

        ImagePlus imout = inimg.duplicate();
        imout.setOverlay(curr_ovl);
        imout.show();
        imout.updateAndDraw();

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
